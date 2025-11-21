import { useMemo, useState } from "react";
import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Chip,
  Input,
  Tab,
  Tabs,
} from "@heroui/react";
import {
  ArrowPathIcon,
  ArrowUpTrayIcon,
  BoltIcon,
  CheckCircleIcon,
  CloudArrowUpIcon,
  ExclamationTriangleIcon,
  PlayCircleIcon,
} from "@heroicons/react/24/outline";

import { useTestFlowStore } from "@/stores/testflow-store";
import { createProject } from "@/lib/testflow-api";

const DashboardPage = () => {
  const artifacts = useTestFlowStore((state) => state.artifacts);
  const issues = useTestFlowStore((state) => state.analysisIssues);
  const scenarios = useTestFlowStore((state) => state.scenarios);
  const executions = useTestFlowStore((state) => state.runnerExecutions);
  const mappingResult = useTestFlowStore((state) => state.mappingResult);
  const startRun = useTestFlowStore((state) => state.startRun);
  const selectedScenarioId = useTestFlowStore(
    (state) => state.selectedScenarioId,
  );

  const latestScenario =
    scenarios.find((scenario) => scenario.id === selectedScenarioId) ??
    scenarios[0];
  const activeRun =
    executions.find(
      (run) => run.status === "running" || run.status === "queued",
    ) ?? executions[0];

  const stats = [
    {
      label: "Артефактов",
      value: artifacts.length,
      detail: `${artifacts.filter((a) => a.status === "ready").length} готовы`,
    },
    {
      label: "Сценариев",
      value: scenarios.length,
      detail: `${latestScenario?.steps.length ?? 0} шагов`,
    },
    {
      label: "Ошибки NLP",
      value: issues.length,
      detail: "требуют внимания",
    },
    {
      label: "Прогоны",
      value: executions.length,
      detail: activeRun ? `последний ${activeRun.status}` : "нет запусков",
    },
  ];

  const [commonValues, setCommonValues] = useState<Record<string, string>>({});
  const [secretValues, setSecretValues] = useState<Record<string, string>>({});

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {stats.map((stat) => (
          <Card
            key={stat.label}
            className="app-card"
          >
            <CardBody className="gap-2">
              <div className="text-xs uppercase tracking-wide text-muted">
                {stat.label}
              </div>
              <div className="text-3xl font-semibold text-[var(--app-text)]">
                {stat.value}
              </div>
              <div className="text-xs text-muted">{stat.detail}</div>
            </CardBody>
          </Card>
        ))}
      </div>

      <ProjectBuilderCard />

      {mappingResult && (
        <Card className="app-card">
          <CardHeader className="justify-between">
            <div>
              <div className="text-sm font-semibold text-[var(--app-text)]">
                Результаты сопоставления (map)
              </div>
              <div className="text-xs text-muted">данные из /map</div>
            </div>
            <Chip size="sm" variant="flat">
              уверенность {(mappingResult.overallConfidence * 100).toFixed(0)}%
            </Chip>
          </CardHeader>
          <CardBody className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <div className="rounded-xl border border-white/10 bg-white/60 p-4">
              <div className="text-xs uppercase text-muted">BPMN задачи</div>
              <div className="text-2xl font-semibold text-[var(--app-text)]">
                {mappingResult.matchedTasks}/{mappingResult.totalTasks}
              </div>
            </div>
            <div className="rounded-xl border border-white/10 bg-white/60 p-4">
              <div className="text-xs uppercase text-muted">OpenAPI эндпоинты</div>
              <div className="text-2xl font-semibold text-[var(--app-text)]">
                {mappingResult.matchedEndpoints}/{mappingResult.totalEndpoints}
              </div>
            </div>
            <div className="rounded-xl border border-white/10 bg-white/60 p-4">
              <div className="text-xs uppercase text-muted">Найдено связей</div>
              <div className="text-2xl font-semibold text-[var(--app-text)]">
                {mappingResult.dataFlowEdges?.length || 0}
              </div>
            </div>
            <div className="rounded-xl border border-white/10 bg-white/60 p-4">
              <div className="text-xs uppercase text-muted">Не сопоставлено</div>
              <div className="text-2xl font-semibold text-[var(--app-text)]">
                {mappingResult.unmatchedTasks?.length || 0}
              </div>
            </div>
          </CardBody>
        </Card>
      )}

      {mappingResult && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <Card className="app-card">
            <CardHeader className="justify-between">
              <div>
                <div className="text-sm font-semibold text-[var(--app-text)]">
                  Сопоставленные задачи
                </div>
                <div className="text-xs text-muted">
                  endpoint, метод, уверенность, стратегия
                </div>
              </div>
              <Chip size="sm" variant="flat">
                всего {Object.keys(mappingResult.taskMappings).length}
              </Chip>
            </CardHeader>
            <CardBody className="space-y-3">
              {Object.values(mappingResult.taskMappings)
                .slice(0, 8)
                .map((task) => (
                  <div
                    key={task.taskId}
                    className="rounded-xl border border-white/10 p-3"
                  >
                    <div className="text-sm font-semibold text-[var(--app-text)]">
                      {task.taskName}
                    </div>
                    <div className="text-xs text-muted">
                      {task.endpointMethod} {task.endpointPath}
                    </div>
                    <div className="mt-1 text-xs text-muted">
                      {(task.confidenceScore * 100).toFixed(0)}% • {task.matchingStrategy}
                    </div>
                    {task.recommendation && (
                      <div className="mt-1 text-xs text-muted">{task.recommendation}</div>
                    )}
                  </div>
                ))}
            </CardBody>
          </Card>

          <Card className="app-card">
            <CardHeader className="justify-between">
              <div>
                <div className="text-sm font-semibold text-[var(--app-text)]">
                  Связи данных
                </div>
                <div className="text-xs text-muted">источники → цели, поля</div>
              </div>
              <Chip size="sm" variant="flat">
                всего {mappingResult.dataFlowEdges?.length || 0}
              </Chip>
            </CardHeader>
            <CardBody className="space-y-3">
              {(mappingResult.dataFlowEdges || []).slice(0, 8).map((edge, index) => (
                <div
                  key={`${edge.sourceTaskId}-${edge.targetTaskId}-${index}`}
                  className="rounded-xl border border-white/10 p-3"
                >
                  <div className="text-sm font-semibold text-[var(--app-text)]">
                    {edge.sourceTaskId} → {edge.targetTaskId}
                  </div>
                  <div className="text-xs text-muted">
                    поля: {edge.fields?.join(", ")}
                  </div>
                  <div className="mt-1 text-xs text-muted">
                    уверенность {(edge.confidence * 100).toFixed(0)}%
                  </div>
                </div>
              ))}
            </CardBody>
          </Card>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card className="app-card">
          <CardHeader className="justify-between">
            <div>
              <div className="text-sm font-semibold text-[var(--app-text)]">
                NLP анализ
              </div>
              <div className="text-xs text-muted">
                несогласованности и точки отказа
              </div>
            </div>
            <Button className="btn-ghost" size="sm">
              Открыть анализ
            </Button>
          </CardHeader>
          <CardBody className="space-y-3">
            {issues.map((issue) => (
              <div
                key={issue.id}
                className="rounded-2xl border border-white/10 p-3"
              >
                <div className="flex items-center gap-2">
                  {issue.severity === "error" ||
                  issue.severity === "critical" ? (
                    <ExclamationTriangleIcon className="h-4 w-4 text-danger" />
                  ) : (
                    <CheckCircleIcon className="h-4 w-4 text-warning" />
                  )}
                  <div className="text-sm font-semibold text-[var(--app-text)]">
                    {issue.title}
                  </div>
                  <Chip size="sm" variant="flat">
                    {issue.category}
                  </Chip>
                </div>
                <div className="mt-2 line-clamp-2 text-xs text-muted">
                  {issue.details}
                </div>
              </div>
            ))}
          </CardBody>
        </Card>
      </div>

      <Card className="app-card">
        <CardHeader className="justify-between">
          <div>
            <div className="text-sm font-semibold text-[var(--app-text)]">
              Общие поля и секреты из OpenAPI
            </div>
          </div>
          <Tabs aria-label="AI insights tabs" size="sm">
            <Tab key="common" title="Общие поля" />
            <Tab key="secret" title="Секреты" />
          </Tabs>
        </CardHeader>
        <CardBody className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {mappingResult?.commonFields && mappingResult.commonFields.length > 0 ? (
            mappingResult.commonFields.slice(0, 4).map((field) => (
            <div
              key={field.fieldName}
              className="rounded-xl border border-white/10 bg-white/60 p-4"
            >
              <div className="text-sm font-semibold text-[var(--app-text)]">
                {field.fieldName}
                {field.required ? " *" : ""}
              </div>
              <div className="text-xs uppercase text-muted">
                {field.fieldType} • {field.required ? "обязательное" : "необязательное"}
              </div>
              <div className="mt-2 line-clamp-2 text-xs text-muted">
                {field.description}
              </div>
              <div className="mt-3 text-xs text-muted">
                {field.usedInEndpoints.length} эндпоинтов
              </div>
              <div className="mt-3">
                <Input
                  label="Значение"
                  placeholder="Введите значение"
                  size="sm"
                  value={commonValues[field.fieldName] ?? ""}
                  onChange={(e) =>
                    setCommonValues((prev) => ({
                      ...prev,
                      [field.fieldName]: e.target.value,
                    }))
                  }
                />
              </div>
            </div>
          ))
          ) : (
            <div className="col-span-2 text-center text-sm text-muted py-8">
              Общие поля не найдены
            </div>
          )}
          {mappingResult?.secretFields && mappingResult.secretFields.length > 0 ? (
            mappingResult.secretFields.map((field) => (
            <div
              key={field.fieldName}
              className="rounded-xl border border-danger/40 bg-danger/10 p-4"
            >
              <div className="text-sm font-semibold text-danger">
                {field.fieldName}
                {field.required ? " *" : ""}
              </div>
              <div className="text-xs uppercase text-danger-200">
                {field.fieldType}
              </div>
              <div className="mt-2 line-clamp-2 text-xs text-danger-200">
                {field.description}
              </div>
              <div className="mt-3">
                <Input
                  label="Секрет"
                  placeholder="Введите секрет"
                  size="sm"
                  type="password"
                  value={secretValues[field.fieldName] ?? ""}
                  onChange={(e) =>
                    setSecretValues((prev) => ({
                      ...prev,
                      [field.fieldName]: e.target.value,
                    }))
                  }
                />
              </div>
            </div>
          ))
          ) : (
            <div className="col-span-2 text-center text-sm text-muted py-8">
              Секретные поля не найдены
            </div>
          )}
        </CardBody>
      </Card>
    </div>
  );
};

export default DashboardPage;

const ProjectBuilderCard = () => {
  const importArtifacts = useTestFlowStore((state) => state.importArtifacts);
  const runMapping = useTestFlowStore((state) => state.runMapping);
  
  const [projectName, setProjectName] = useState("");
  const [openApiFile, setOpenApiFile] = useState<File | null>(null);
  const [bpmnFile, setBpmnFile] = useState<File | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isReady = useMemo(() => openApiFile && bpmnFile, [openApiFile, bpmnFile]);

  const handleSubmit = async () => {
    if (!openApiFile || !bpmnFile) {
      setError("Загрузите оба файла");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const bufToText = async (file: File) => {
        const buffer = new Uint8Array(await file.arrayBuffer());
        const tryDecode = (enc: string) => {
          try { return new TextDecoder(enc, { fatal: false }).decode(buffer); } catch { return ""; }
        };
        const hasRep = (s: string) => (s.match(/[\uFFFD]/g)?.length ?? 0) > 5;
        let text = tryDecode("utf-8");
        if (!text || hasRep(text)) {
          for (const enc of ["windows-1251", "utf-16le", "utf-16be"]) {
            const alt = tryDecode(enc);
            if (alt && !hasRep(alt)) { text = alt; break; }
          }
        }
        return text || "";
      };
      const openApiText = await bufToText(openApiFile);
      const bpmnText = await bufToText(bpmnFile);

      try {
        await createProject(projectName || "Новый проект", bpmnText, openApiText);
      } catch (_) {
        // игнорируем ошибку создания проекта, чтобы пользователь мог продолжить анализ
      }

      await importArtifacts([openApiFile, bpmnFile]);

      setProjectName("");
      setOpenApiFile(null);
      setBpmnFile(null);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Не удалось создать проект",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRemap = async () => {
    if (!openApiFile || !bpmnFile) {
      setError("Загрузите оба файла");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      const bufToText = async (file: File) => {
        const buffer = new Uint8Array(await file.arrayBuffer());
        const tryDecode = (enc: string) => {
          try { return new TextDecoder(enc, { fatal: false }).decode(buffer); } catch { return ""; }
        };
        const hasRep = (s: string) => (s.match(/[\uFFFD]/g)?.length ?? 0) > 5;
        let text = tryDecode("utf-8");
        if (!text || hasRep(text)) {
          for (const enc of ["windows-1251", "utf-16le", "utf-16be"]) {
            const alt = tryDecode(enc);
            if (alt && !hasRep(alt)) { text = alt; break; }
          }
        }
        return text || "";
      };
      const payload = {
        bpmnXml: await bufToText(bpmnFile),
        openApiJson: await bufToText(openApiFile),
      };
      await runMapping(payload, { scenarioName: projectName || undefined });
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Не удалось выполнить повторный маппинг",
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Card className="app-card">
      <CardHeader className="flex-col items-start gap-2">
        <div className="flex items-center gap-2 text-sm font-semibold text-[var(--app-text)]">
          <CloudArrowUpIcon className="h-5 w-5 text-[var(--app-primary)]" />
          Новый проект тестирования
        </div>
        <p className="text-sm text-muted">
          Соберите OpenAPI + BPMN, чтобы получить автосгенерированный сценарий
          и цепочку.
        </p>
      </CardHeader>
      <CardBody className="space-y-4">
        <Input
          label="Название проекта"
          placeholder="VTB Cards Regression"
          value={projectName}
          onChange={(event) => setProjectName(event.target.value)}
        />
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <FileField
            accept=".json,.yaml,.yml"
            description="OpenAPI json/yaml"
            file={openApiFile}
            label="OpenAPI"
            onFileChange={setOpenApiFile}
          />
          <FileField
            accept=".bpmn,.xml"
            description="BPMN .bpmn/.xml"
            file={bpmnFile}
            label="BPMN"
            onFileChange={setBpmnFile}
          />
        </div>
        {error && <div className="text-sm text-danger">{error}</div>}
        <div className="flex gap-2">
          <Button
            className="btn-primary"
            isDisabled={!isReady || isSubmitting}
            isLoading={isSubmitting}
            onPress={handleSubmit}
          >
            Создать проект и запустить анализ
          </Button>
          <Button
            className="btn-outline"
            isDisabled={!isReady || isSubmitting}
            isLoading={isSubmitting}
            startContent={<ArrowPathIcon className="h-4 w-4" />}
            onPress={handleRemap}
          >
            Повторный маппинг
          </Button>
        </div>
      </CardBody>
    </Card>
  );
};

const FileField = ({
  label,
  description,
  accept,
  file,
  onFileChange,
}: {
  label: string;
  description: string;
  accept: string;
  file: File | null;
  onFileChange: (file: File | null) => void;
}) => (
  <label className="flex cursor-pointer flex-col gap-2 rounded-2xl border border-dashed border-white/20 bg-white/40 p-4 text-sm text-[var(--app-text)]">
    <span className="font-semibold">{label}</span>
    <span className="text-xs text-muted">{description}</span>
    <div className="rounded-xl border border-white/40 bg-white/60 px-3 py-2 text-xs text-[var(--app-text)]">
      {file ? file.name : "Перетащите файл или нажмите, чтобы выбрать"}
    </div>
    <input
      accept={accept}
      className="hidden"
      type="file"
      onChange={(event) => onFileChange(event.target.files?.[0] ?? null)}
    />
  </label>
);

