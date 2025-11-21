import { Card, CardBody, CardHeader, Chip, Button } from "@heroui/react";
import {
  ExclamationTriangleIcon,
  CheckCircleIcon,
  XMarkIcon,
} from "@heroicons/react/24/outline";
import { ProjectDto, startAiVerification, getAiStatus, listAiJobs } from "@/lib/testflow-api";
import { useTestFlowStore } from "@/stores/testflow-store";
import { useEffect, useMemo, useRef, useState } from "react";

interface ProjectAnalysisTabProps {
  project: ProjectDto;
}

export function ProjectAnalysisTab({ project }: ProjectAnalysisTabProps) {
  const [tasks, setTasks] = useState<Array<{ jobId: string; createdAt?: string; startedAt?: string; finishedAt?: string; modelName?: string; status: "queued" | "running" | "completed" | "error"; result?: any; error?: string }>>([]);
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null);
  const timersRef = useRef<Record<string, any>>({});
  const selectedModelId = useTestFlowStore((state) => (state as any).selectedModelId as number | null);

  useEffect(() => {
    (async () => {
      try {
        const list = await listAiJobs(project.id);
        const jobs = (list.jobs || []).map((j) => ({
          jobId: j.id,
          status: (j.status as any) as "queued" | "running" | "completed" | "error",
          createdAt: j.createdAt ? String(j.createdAt) : undefined,
          startedAt: j.startedAt ? String(j.startedAt) : undefined,
          finishedAt: j.finishedAt ? String(j.finishedAt) : undefined,
          modelName: j.modelName,
          result: j.result,
          error: j.error,
        }));
        setTasks(jobs);
      } catch {}
    })();
  }, [project.id]);

  useEffect(() => {}, [tasks, project.id]);

  useEffect(() => {
    tasks.forEach((t) => {
      if (t.status === "completed" || t.status === "error") return;
      if (timersRef.current[t.jobId]) return;
      const timer = setInterval(async () => {
        try {
          const status = await getAiStatus(t.jobId);
          setTasks((prev) => prev.map((p) => (p.jobId === t.jobId ? { ...p, status: status.status, result: status.result, error: status.error, createdAt: status.createdAt ? String(status.createdAt) : p.createdAt, startedAt: status.startedAt ? String(status.startedAt) : p.startedAt, finishedAt: status.finishedAt ? String(status.finishedAt) : p.finishedAt, modelName: status.modelName ?? p.modelName } : p)));
          if (status.status === "completed" || status.status === "error") {
            clearInterval(timersRef.current[t.jobId]);
            delete timersRef.current[t.jobId];
          }
        } catch (e) {
          setTasks((prev) => prev.map((p) => (p.jobId === t.jobId ? { ...p, status: "error", error: e instanceof Error ? e.message : "Ошибка" } : p)));
          clearInterval(timersRef.current[t.jobId]);
          delete timersRef.current[t.jobId];
        }
      }, 2000);
      timersRef.current[t.jobId] = timer;
    });
    return () => {
      Object.values(timersRef.current).forEach((tm) => clearInterval(tm));
      timersRef.current = {};
    };
  }, [tasks]);

  const running = useMemo(() => tasks.some((t) => t.status === "queued" || t.status === "running"), [tasks]);

  const activeReport = useMemo(() => {
    if (selectedJobId) {
      const found = tasks.find((t) => t.jobId === selectedJobId && t.result);
      if (found?.result) return found.result;
    }
    const lastCompleted = [...tasks].reverse().find((t) => t.status === "completed" && t.result);
    return lastCompleted?.result ?? project.mappingResult?.aiVerificationReport;
  }, [tasks, project.mappingResult, selectedJobId]);

  type Issue = {
    id: string;
    title: string;
    category: string;
    severity: "error" | "warning";
    details: string;
    artifactRef?: string;
    stepRef?: string;
  };

  const issues: Issue[] = [
    ...(project.mappingResult?.unmatchedTasks?.map((task) => ({
      id: task.elementId,
      title: `Несогласованность: ${task.elementName}`,
      category: "Endpoint mismatches" as const,
      severity: "error" as const,
      details: task.recommendations.join(", "),
      artifactRef: "BPMN",
      stepRef: task.elementId,
    })) ?? []),
    ...(activeReport?.openapi?.errors?.map((error: string, idx: number) => ({
      id: `openapi-error-${idx}`,
      title: `Ошибка OpenAPI: ${error}`,
      category: "Missing validation" as const,
      severity: "error" as const,
      details: error,
      artifactRef: "OpenAPI",
    })) ?? []),
    ...(activeReport?.bpmn?.errors?.map((error: string, idx: number) => ({
      id: `bpmn-error-${idx}`,
      title: `Ошибка BPMN: ${error}`,
      category: "Dead path" as const,
      severity: "error" as const,
      details: error,
      artifactRef: "BPMN",
    })) ?? []),
  ];

  const groupedIssues = issues.reduce(
    (acc, issue) => {
      if (!acc[issue.category]) {
        acc[issue.category] = [];
      }
      acc[issue.category].push(issue);
      return acc;
    },
    {} as Record<string, Issue[]>,
  );

  return (
    <div className="space-y-4">
      <Card className="app-card">
        <CardHeader className="justify-between pb-2">
          <div className="flex items-center gap-2">
            <div className="text-sm font-semibold text-[var(--app-text)]">История анализов</div>
            {running && (
              <Chip size="sm" variant="flat" color="primary">
                Выполняется…
              </Chip>
            )}
          </div>
          <Button
            className="btn-primary"
            size="sm"
            isDisabled={running}
            isLoading={running}
          onPress={async () => {
            try {
              const payload = { bpmnXml: project.bpmnXml, openApiJson: project.openApiJson } as any;
                const { jobId } = await startAiVerification(payload, selectedModelId ?? undefined, project.id);
                const task = { jobId, createdAt: new Date().toISOString(), status: "queued" as const };
                setTasks((prev) => [task, ...prev]);
                setSelectedJobId(jobId);
              } catch {}
            }}
          >
            Запустить анализ
          </Button>
        </CardHeader>
        <CardBody className="pt-0">
          {tasks.length === 0 ? (
            <div className="text-xs text-muted py-4 text-center">
              История запусков пуста. Нажмите "Запустить анализ" для начала.
            </div>
          ) : (
            <div className="space-y-2 max-h-64 overflow-y-auto">
              {tasks.map((t) => (
                <button
                  key={t.jobId}
                  className={`w-full text-left p-3 rounded-lg border transition-colors ${
                    selectedJobId === t.jobId
                      ? "border-primary/60 bg-primary/10"
                      : "border-white/10 hover:bg-white/5 hover:border-white/20"
                  }`}
                  onClick={() => setSelectedJobId(t.jobId)}
                >
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <div className="flex items-center gap-2">
                      <Chip
                        size="sm"
                        variant="flat"
                        color={
                          t.status === "completed" ? "success" :
                          t.status === "running" ? "primary" :
                          t.status === "queued" ? "warning" : "danger"
                        }
                      >
                        {t.status === "completed"
                          ? "Завершено"
                          : t.status === "running"
                            ? "В процессе"
                            : t.status === "queued"
                              ? "В очереди"
                              : "Ошибка"}
                      </Chip>
                      {t.modelName && (
                        <span className="text-xs text-muted">{t.modelName}</span>
                      )}
                    </div>
                    <div className="text-[10px] text-muted font-mono truncate max-w-[200px]">
                      {t.jobId}
                    </div>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-2 text-xs text-muted break-words">
                    <div>
                      <div className="text-[10px] mb-0.5">Создан</div>
                      <div>{t.createdAt ? new Date(t.createdAt).toLocaleString() : "—"}</div>
                    </div>
                    {t.finishedAt && (
                      <div>
                        <div className="text-[10px] mb-0.5">Завершён</div>
                        <div>{new Date(t.finishedAt).toLocaleString()}</div>
                      </div>
                    )}
                    {t.result?.overallStatus && (
                      <div>
                        <div className="text-[10px] mb-0.5">Статус</div>
                        <div className="truncate">{t.result.overallStatus}</div>
                      </div>
                    )}
                  </div>
                </button>
              ))}
            </div>
          )}
        </CardBody>
      </Card>
      {activeReport && (
        <Card className="app-card">
          <CardHeader className="justify-between pb-2">
            <div>
              <div className="text-sm font-semibold text-[var(--app-text)]">Итоги анализа</div>
              <div className="text-xs text-muted">сводка по отчёту</div>
            </div>
            <div className="flex gap-2">
              {typeof activeReport.totalErrors === "number" && (
                <Chip size="sm" color="danger" variant="flat">Ошибок: {activeReport.totalErrors}</Chip>
              )}
              {typeof activeReport.totalWarnings === "number" && (
                <Chip size="sm" color="warning" variant="flat">Предупреждений: {activeReport.totalWarnings}</Chip>
              )}
              {typeof activeReport.totalSuggestions === "number" && (
                <Chip size="sm" variant="flat">Рекомендаций: {activeReport.totalSuggestions}</Chip>
              )}
            </div>
          </CardHeader>
        </Card>
      )}

      {activeReport?.rawModelOutput && (
        <Card className="app-card">
          <CardHeader className="justify-between pb-2">
            <div>
              <div className="text-sm font-semibold text-[var(--app-text)]">Ответ модели</div>
              <div className="text-xs text-muted">сырой вывод генерации</div>
            </div>
          </CardHeader>
          <CardBody className="pt-0">
            <pre className="text-xs whitespace-pre-wrap break-words text-[var(--app-text)] bg-white/5 rounded-lg p-3">
              {String(activeReport.rawModelOutput)}
            </pre>
          </CardBody>
        </Card>
      )}

      {(activeReport?.openapi || activeReport?.bpmn) && (
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          {activeReport?.openapi && (
            <Card className="app-card">
              <CardHeader className="justify-between pb-2">
                <div>
                  <div className="text-sm font-semibold text-[var(--app-text)]">Проверка OpenAPI</div>
                  <div className="text-xs text-muted">контракт сервиса</div>
                </div>
                <Chip size="sm" variant="flat">{activeReport.openapi.status}</Chip>
              </CardHeader>
              <CardBody className="pt-0 space-y-3">
                {activeReport.openapi.summary && (
                  <div className="text-xs text-muted">{activeReport.openapi.summary}</div>
                )}
                {activeReport.openapi.errors?.length > 0 && (
                  <div>
                    <div className="text-xs uppercase text-muted mb-1">Ошибки</div>
                    <div className="space-y-2">
                      {activeReport.openapi.errors.map((e: string, i: number) => (
                        <div key={`oa-err-${i}`} className="rounded-lg border border-danger/30 bg-danger/5 p-3 text-xs text-[var(--app-text)]">
                          {e}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {activeReport.openapi.warnings?.length > 0 && (
                  <div>
                    <div className="text-xs uppercase text-muted mb-1">Предупреждения</div>
                    <div className="space-y-2">
                      {activeReport.openapi.warnings.map((w: string, i: number) => (
                        <div key={`oa-warn-${i}`} className="rounded-lg border border-warning/30 bg-warning/5 p-3 text-xs text-[var(--app-text)]">
                          {w}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {activeReport.openapi.suggestions?.length > 0 && (
                  <div>
                    <div className="text-xs uppercase text-muted mb-1">Рекомендации</div>
                    <div className="space-y-2">
                      {activeReport.openapi.suggestions.map((s: string, i: number) => (
                        <div key={`oa-sugg-${i}`} className="rounded-lg border border-white/10 bg-white/5 p-3 text-xs text-[var(--app-text)]">
                          {s}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </CardBody>
            </Card>
          )}

          {activeReport?.bpmn && (
            <Card className="app-card">
              <CardHeader className="justify-between pb-2">
                <div>
                  <div className="text-sm font-semibold text-[var(--app-text)]">Проверка BPMN</div>
                  <div className="text-xs text-muted">бизнес-процесс</div>
                </div>
                <Chip size="sm" variant="flat">{activeReport.bpmn.status}</Chip>
              </CardHeader>
              <CardBody className="pt-0 space-y-3">
                {activeReport.bpmn.summary && (
                  <div className="text-xs text-muted">{activeReport.bpmn.summary}</div>
                )}
                {activeReport.bpmn.errors?.length > 0 && (
                  <div>
                    <div className="text-xs uppercase text-muted mb-1">Ошибки</div>
                    <div className="space-y-2">
                      {activeReport.bpmn.errors.map((e: string, i: number) => (
                        <div key={`bpmn-err-${i}`} className="rounded-lg border border-danger/30 bg-danger/5 p-3 text-xs text-[var(--app-text)]">
                          {e}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {activeReport.bpmn.warnings?.length > 0 && (
                  <div>
                    <div className="text-xs uppercase text-muted mb-1">Предупреждения</div>
                    <div className="space-y-2">
                      {activeReport.bpmn.warnings.map((w: string, i: number) => (
                        <div key={`bpmn-warn-${i}`} className="rounded-lg border border-warning/30 bg-warning/5 p-3 text-xs text-[var(--app-text)]">
                          {w}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {activeReport.bpmn.suggestions?.length > 0 && (
                  <div>
                    <div className="text-xs uppercase text-muted mb-1">Рекомендации</div>
                    <div className="space-y-2">
                      {activeReport.bpmn.suggestions.map((s: string, i: number) => (
                        <div key={`bpmn-sugg-${i}`} className="rounded-lg border border-white/10 bg-white/5 p-3 text-xs text-[var(--app-text)]">
                          {s}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </CardBody>
            </Card>
          )}
        </div>
      )}

      {Object.entries(groupedIssues).map(([category, categoryIssues]) => (
        <Card key={category} className="app-card">
          <CardHeader className="pb-2">
            <div className="flex-1">
              <div className="text-xs font-semibold text-[var(--app-text)] uppercase tracking-wide">
                {category}
              </div>
              <div className="text-xs text-muted mt-0.5">
                {categoryIssues.length} {categoryIssues.length === 1 ? "проблема" : "проблем"}
              </div>
            </div>
          </CardHeader>
          <CardBody className="pt-0 space-y-2">
            {categoryIssues.map((issue) => (
              <div
                key={issue.id}
                className={`rounded-lg border p-3 transition-colors ${
                  issue.severity === "error"
                    ? "border-danger/30 bg-danger/5"
                    : "border-warning/30 bg-warning/5"
                }`}
              >
                <div className="flex items-start gap-2">
                  {issue.severity === "error" ? (
                    <ExclamationTriangleIcon className="w-4 h-4 text-danger shrink-0 mt-0.5" />
                  ) : (
                    <CheckCircleIcon className="w-4 h-4 text-warning shrink-0 mt-0.5" />
                  )}
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-semibold text-[var(--app-text)] mb-1">
                      {issue.title}
                    </div>
                    <div className="text-xs text-muted mb-2 line-clamp-2">
                      {issue.details}
                    </div>
                    <div className="flex items-center gap-2 flex-wrap">
                      {issue.artifactRef && (
                        <Chip size="sm" variant="flat" color="primary">
                          {issue.artifactRef}
                        </Chip>
                      )}
                      {issue.stepRef && (
                        <Chip size="sm" variant="flat">
                          Шаг: {issue.stepRef}
                        </Chip>
                      )}
                    </div>
                  </div>
                  <div className="flex gap-1 shrink-0">
                    <Button 
                      size="sm" 
                      variant="light" 
                      className="text-[var(--app-primary)] text-xs"
                    >
                      Исправить
                    </Button>
                    <Button
                      size="sm"
                      variant="light"
                      isIconOnly
                      className="text-muted"
                      onPress={() => {
                        // Пометить как ложное срабатывание
                      }}
                    >
                      <XMarkIcon className="w-3 h-3" />
                    </Button>
                  </div>
                </div>
              </div>
            ))}
          </CardBody>
        </Card>
      ))}

      {issues.length === 0 && (
        <Card className="app-card">
          <CardBody className="text-center py-12">
            <CheckCircleIcon className="w-12 h-12 text-success mx-auto mb-4" />
            <div className="text-muted">
              Проблем не найдено. Анализ пройден успешно.
            </div>
          </CardBody>
        </Card>
      )}
    </div>
  );
}

