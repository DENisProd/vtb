import { useState, useMemo } from "react";
import {
  Card,
  CardBody,
  CardHeader,
  Chip,
  Button,
  Input,
  Select,
  SelectItem,
  Tabs,
  Tab,
} from "@heroui/react";
import { PlayCircleIcon, ArrowPathIcon } from "@heroicons/react/24/outline";
import { ProjectDto } from "@/lib/testflow-api";
import { executeTest } from "@/lib/testflow-api";
import { useTestFlowStore } from "@/stores/testflow-store";
import type {
  TestExecutionResult,
  TestExecutionStepResult,
  ExecutionProblem,
  TestDataGenerationResult,
} from "@/types/testflow";

interface ProjectRunnerTabProps {
  project: ProjectDto;
}

export function ProjectRunnerTab({ project }: ProjectRunnerTabProps) {
  const templates = useTestFlowStore((state) => state.templates);
  const activeTemplateId = useTestFlowStore((state) => state.activeTemplateId);
  const globalOverrides = useTestFlowStore((state) => state.globalOverrides);
  const commonOverrides = useTestFlowStore((state) => state.commonOverrides);

  const [baseUrl, setBaseUrl] = useState("http://localhost:8080");
  const [variantIndex, setVariantIndex] = useState(0);
  const [stopOnFirstError, setStopOnFirstError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<TestExecutionResult | null>(null);

  const activeTemplate = useMemo(
    () =>
      templates.find((template) => template.id === activeTemplateId) ??
      templates[0],
    [templates, activeTemplateId],
  );

  const mappingResult = project.mappingResult;

  const handleExecute = async () => {
    if (!mappingResult || !activeTemplate || !project.openApiJson || !project.bpmnXml) {
      setError("Необходимы маппинг, тестовые данные, OpenAPI и BPMN");
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      // Преобразуем template в формат TestDataGenerationResult
      // Создаем варианты данных из контекстов шаблона
      const stepDataMap = new Map<
        string,
        {
          taskId: string;
          taskName: string;
          requestData?: Record<string, unknown>;
          queryParams?: Record<string, unknown>;
          responseData?: Record<string, unknown>;
        }
      >();

      // Группируем поля по шагам
      for (const ctx of activeTemplate.contexts) {
        if (ctx.scope === "step" && ctx.relatedStepId) {
          const existing =
            stepDataMap.get(ctx.relatedStepId) || {
              taskId: ctx.relatedStepId,
              taskName: ctx.label,
            };

          const requestDataField = ctx.fields.find((f) => f.key === "requestData");
          if (requestDataField?.value) {
            existing.requestData = requestDataField.value as Record<
              string,
              unknown
            >;
          }

          const queryParamsField = ctx.fields.find((f) => f.key === "queryParams");
          if (queryParamsField?.value) {
            existing.queryParams = queryParamsField.value as Record<
              string,
              unknown
            >;
          }

          const responseDataField = ctx.fields.find((f) => f.key === "responseData");
          if (responseDataField?.value) {
            existing.responseData = responseDataField.value as Record<
              string,
              unknown
            >;
          }

          stepDataMap.set(ctx.relatedStepId, existing);
        }
      }

      for (const [k, v] of Object.entries(globalOverrides ?? {})) {
        const [sid, fname] = k.split(".");
        if (!sid || !fname) continue;
        const existing =
          stepDataMap.get(sid) || {
            taskId: sid,
            taskName: sid,
          };
        existing.requestData = { ...(existing.requestData ?? {}), [fname]: v };
        stepDataMap.set(sid, existing);
      }

      for (const [fname, v] of Object.entries(commonOverrides ?? {})) {
        for (const [sid, existingStep] of stepDataMap.entries()) {
          const updated = { ...(existingStep.requestData ?? {}), [fname]: v };
          stepDataMap.set(sid, { ...existingStep, requestData: updated });
        }
      }

      const testData: TestDataGenerationResult = {
        generationType: "CLASSIC",
        scenario: "positive",
        variants: [Array.from(stepDataMap.values())],
      };

      const testDataJson = JSON.stringify(testData);
      const mappingResultJson = JSON.stringify(mappingResult);

      const executionResult = await executeTest({
        bpmnXml: project.bpmnXml,
        openApiJson: project.openApiJson,
        testDataJson,
        mappingResultJson,
        baseUrl,
        variantIndex,
        stopOnFirstError,
      });

      setResult(executionResult);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Ошибка выполнения теста");
    } finally {
      setLoading(false);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "SUCCESS":
        return "success";
      case "FAILED":
        return "danger";
      case "PARTIAL":
        return "warning";
      default:
        return "default";
    }
  };

  return (
    <div className="space-y-4">
      <Card className="app-card">
        <CardHeader className="justify-between">
          <div>
            <div className="text-sm font-semibold text-[var(--app-text)]">
              Автотестирование API
            </div>
            <div className="text-xs text-muted">
              Выполнение тестовых сценариев с валидацией ответов
            </div>
          </div>
        </CardHeader>
        <CardBody className="space-y-4">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Input
              label="Base URL"
              placeholder="http://localhost:8080"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
            />
            <Input
              label="Индекс варианта данных"
              type="number"
              value={variantIndex.toString()}
              onChange={(e) => setVariantIndex(Number(e.target.value) || 0)}
            />
            <Select
              label="Остановка при ошибке"
              selectedKeys={[stopOnFirstError ? "true" : "false"]}
              onSelectionChange={(keys) => {
                const selected = Array.from(keys)[0] === "true";
                setStopOnFirstError(selected);
              }}
            >
              <SelectItem key="false">
                Нет
              </SelectItem>
              <SelectItem key="true">
                Да
              </SelectItem>
            </Select>
          </div>
          <Button
            className="btn-primary"
            isDisabled={
              loading ||
              !mappingResult ||
              !activeTemplate ||
              !project.openApiJson ||
              !project.bpmnXml
            }
            isLoading={loading}
            startContent={
              loading ? undefined : <PlayCircleIcon className="w-4 h-4" />
            }
            onPress={handleExecute}
          >
            {loading ? "Выполнение..." : "Запустить тесты"}
          </Button>
        </CardBody>
      </Card>

      {error && (
        <Card className="app-card border-danger/30 bg-danger/5">
          <CardBody>
            <div className="text-sm text-danger">{error}</div>
          </CardBody>
        </Card>
      )}

      {result && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <Card className="app-card lg:col-span-2">
            <CardHeader>
              <div>
                <div className="text-sm font-semibold text-[var(--app-text)]">
                  Результаты выполнения
                </div>
                <div className="text-xs text-muted">
                  {result.processName || project.name}
                </div>
              </div>
              <Chip color={getStatusColor(result.status)} size="sm">
                {result.status}
              </Chip>
            </CardHeader>
            <CardBody>
              <Tabs>
                <Tab key="steps" title="Шаги">
                  <div className="mt-4 space-y-3">
                    {result.steps.map((step, index) => (
                      <StepResultCard
                        key={step.taskId || index}
                        step={step}
                      />
                    ))}
                  </div>
                </Tab>
                <Tab
                  key="problems"
                  title={`Проблемы (${result.problems?.length || 0})`}
                >
                  <div className="mt-4 space-y-3">
                    {result.problems && result.problems.length > 0 ? (
                      result.problems.map((problem, index) => (
                        <ProblemCard key={index} problem={problem} />
                      ))
                    ) : (
                      <div className="text-sm text-muted">
                        Проблем не обнаружено
                      </div>
                    )}
                  </div>
                </Tab>
                <Tab key="statistics" title="Статистика">
                  {result.statistics && (
                    <div className="mt-4 space-y-3">
                      <div className="grid grid-cols-2 gap-4">
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-muted">Всего шагов</div>
                          <div className="text-lg font-semibold text-[var(--app-text)]">
                            {result.statistics.totalSteps}
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-muted">Успешных</div>
                          <div className="text-lg font-semibold text-success">
                            {result.statistics.successfulSteps}
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-muted">Ошибок</div>
                          <div className="text-lg font-semibold text-danger">
                            {result.statistics.failedSteps}
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-muted">Пропущено</div>
                          <div className="text-lg font-semibold text-muted">
                            {result.statistics.skippedSteps}
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-muted">Среднее время</div>
                          <div className="text-lg font-semibold text-[var(--app-text)]">
                            {Math.round(result.statistics.averageStepDurationMs)}ms
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-muted">Всего запросов</div>
                          <div className="text-lg font-semibold text-[var(--app-text)]">
                            {result.statistics.totalRequests}
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                </Tab>
              </Tabs>
            </CardBody>
          </Card>

          <Card className="app-card">
            <CardHeader>
              <div className="text-sm font-semibold text-[var(--app-text)]">
                Общая информация
              </div>
            </CardHeader>
            <CardBody className="space-y-3">
              <div>
                <div className="text-xs text-muted">Статус</div>
                <Chip
                  color={getStatusColor(result.status)}
                  size="sm"
                  className="mt-1"
                >
                  {result.status}
                </Chip>
              </div>
              <div>
                <div className="text-xs text-muted">Длительность</div>
                <div className="text-sm font-semibold text-[var(--app-text)]">
                  {result.totalDurationMs}ms
                </div>
              </div>
              <div>
                <div className="text-xs text-muted">Начало</div>
                <div className="text-sm text-[var(--app-text)]">
                  {new Date(result.startTime).toLocaleString()}
                </div>
              </div>
              {result.endTime && (
                <div>
                  <div className="text-xs text-muted">Окончание</div>
                  <div className="text-sm text-[var(--app-text)]">
                    {new Date(result.endTime).toLocaleString()}
                  </div>
                </div>
              )}
            </CardBody>
          </Card>
        </div>
      )}
    </div>
  );
}

const StepResultCard = ({ step }: { step: TestExecutionStepResult }) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="rounded-lg border border-white/10 p-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Chip
            color={
              step.status === "SUCCESS"
                ? "success"
                : step.status === "FAILED"
                  ? "danger"
                  : "default"
            }
            size="sm"
          >
            {step.status}
          </Chip>
          <div>
            <div className="text-sm font-semibold text-[var(--app-text)]">
              {step.taskName || step.taskId}
            </div>
            <div className="text-xs text-muted">{step.durationMs}ms</div>
          </div>
        </div>
        <Button
          size="sm"
          variant="light"
          onPress={() => setExpanded(!expanded)}
        >
          {expanded ? "Свернуть" : "Детали"}
        </Button>
      </div>
      {expanded && (
        <div className="mt-3 space-y-2 border-t border-white/10 pt-3">
          {step.request && (
            <div>
              <div className="text-xs text-muted mb-1">Запрос</div>
              <div className="text-xs text-[var(--app-text)] font-mono bg-white/5 p-2 rounded">
                <div>
                  {step.request.method} {step.request.url}
                </div>
                {step.request.body && (
                  <pre className="mt-1 text-xs overflow-auto">
                    {step.request.body}
                  </pre>
                )}
              </div>
            </div>
          )}
          {step.response && (
            <div>
              <div className="text-xs text-muted mb-1">Ответ</div>
              <div className="text-xs text-[var(--app-text)] font-mono bg-white/5 p-2 rounded">
                <div>
                  Status: {step.response.statusCode} (
                  {step.response.responseTimeMs}ms)
                </div>
                {step.response.body && (
                  <pre className="mt-1 text-xs overflow-auto">
                    {step.response.body}
                  </pre>
                )}
              </div>
            </div>
          )}
          {step.validation && (
            <div>
              <div className="text-xs text-muted mb-1">Валидация</div>
              <div className="text-xs">
                <Chip
                  color={step.validation.isValid ? "success" : "danger"}
                  size="sm"
                >
                  {step.validation.isValid ? "Валидно" : "Ошибки"}
                </Chip>
                {step.validation.errors &&
                  step.validation.errors.length > 0 && (
                    <div className="mt-2 space-y-1">
                      {step.validation.errors.map((err, idx) => (
                        <div key={idx} className="text-danger text-xs">
                          • {err}
                        </div>
                      ))}
                    </div>
                  )}
              </div>
            </div>
          )}
          {step.errorMessage && (
            <div>
              <div className="text-xs text-danger">{step.errorMessage}</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

const ProblemCard = ({ problem }: { problem: ExecutionProblem }) => {
  return (
    <div className="rounded-lg border border-danger/30 bg-danger/5 p-3">
      <div className="flex items-center gap-2 mb-2">
        <Chip
          color={problem.severity === "ERROR" ? "danger" : "warning"}
          size="sm"
        >
          {problem.severity}
        </Chip>
        <Chip size="sm" variant="flat">
          {problem.type}
        </Chip>
      </div>
      <div className="text-sm font-semibold text-[var(--app-text)] mb-1">
        {problem.stepName || problem.stepId}
      </div>
      <div className="text-xs text-muted mb-2">{problem.message}</div>
      {problem.details && (
        <div className="text-xs text-muted font-mono bg-white/5 p-2 rounded">
          {problem.details}
        </div>
      )}
      {problem.requestUrl && (
        <div className="text-xs text-muted mt-2">
          {problem.requestMethod} {problem.requestUrl}
        </div>
      )}
    </div>
  );
};
