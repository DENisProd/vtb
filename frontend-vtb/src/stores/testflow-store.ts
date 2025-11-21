import { create } from "zustand";
import { devtools } from "zustand/middleware";

import type {
  Artifact,
  LogEntry,
  MappingPayload,
  ProcessNode,
  RunnerExecution,
  StepExecutionStatus,
  TestFlowState,
  TestDataContext,
  TestDataField,
  TestDataTemplate,
  MappingResultDto,
} from "@/types/testflow";
import { requestMapping, triggerScenarioRun, startAiVerification, getAiStatus, fetchAiModels, generateTestData } from "@/lib/testflow-api";
 
import {
  mappingToAnalysisIssues,
  mappingToProcessGraph,
  mappingToScenario,
} from "@/utils/mappingTransform";

interface TestFlowStore extends TestFlowState {
  initialize: () => void;
  loadAiModels: () => Promise<void>;
  setSelectedModelId: (modelId: number | null) => void;
  setSelectedScenario: (scenarioId: string | null) => void;
  importArtifacts: (files: File[]) => Promise<void>;
  runMapping: (
    payload: MappingPayload,
    options?: { scenarioName?: string },
  ) => Promise<void>;
  buildGraphFromMapping: () => void;
  generateTestDataTemplates: (options?: { openApiJson?: string; generationType?: "CLASSIC" | "AI"; scenario?: string; variantsCount?: number; mappingResult?: MappingResultDto }) => Promise<void>;
  setActiveTemplate: (templateId: string) => void;
  setGlobalOverride: (stepId: string, field: string, value: unknown) => void;
  setCommonOverride: (fieldName: string, value: unknown) => void;
  startRun: (
    scenarioId: string,
    options?: { parallelism?: number; templateId?: string },
  ) => Promise<void>;
  addScenarioStep: (
    scenarioId: string,
    step: { title: string; endpoint: string; method: string; expectedStatus?: number },
  ) => void;
  connectScenarioSteps: (
    scenarioId: string,
    sourceStepId: string,
    targetStepId: string,
  ) => void;
  setSelectedExecution: (executionId: string | null) => void;
  upsertExecution: (execution: RunnerExecution) => void;
  appendLog: (executionId: string, entry: LogEntry) => void;
  updateNodeStatus: (stepId: string, status: StepExecutionStatus) => void;
  updateScenarioStepStatus: (
    scenarioId: string,
    stepId: string,
    status: StepExecutionStatus,
  ) => void;
  updateProcessNodePosition: (
    nodeId: string,
    position: { x: number; y: number },
  ) => void;
}

const initialState: TestFlowState = {
  artifacts: [],
  analysisIssues: [],
  scenarios: [],
  selectedScenarioId: null,
  templates: [],
  activeTemplateId: null,
  runnerExecutions: [],
  selectedExecutionId: null,
  processNodes: [],
  processEdges: [],
  mappingResult: undefined,
  loading: false,
  error: null,
  openApiJson: undefined,
  bpmnXml: undefined,
  globalOverrides: {},
  commonOverrides: {},
};

// No persistence required

const getDefaultStatus = (method: string) => {
  const map: Record<string, number> = {
    GET: 200,
    POST: 201,
    PUT: 200,
    PATCH: 200,
    DELETE: 204,
  };

  return map[method.toUpperCase()] ?? 200;
};

const getGridPosition = (index: number): ProcessNode["position"] => {
  const col = index % 4;
  const row = Math.floor(index / 4);

  return {
    x: col * 220 + 140,
    y: row * 140 + 140,
  };
};

export const useTestFlowStore = create<TestFlowStore>()(
  devtools(
    (set, get) => ({
      ...initialState,

      initialize: () => {
        set(initialState);
      },

      buildGraphFromMapping: () => {
        const state = get();
        if (!state.mappingResult) return;
        // Всегда пересоздаем граф из mappingResult, если он есть
        const { nodes, edges } = mappingToProcessGraph(state.mappingResult);
        set({ processNodes: nodes, processEdges: edges });
      },

      generateTestDataTemplates: async (options) => {
        const state = get();
        const mapping = options?.mappingResult ?? state.mappingResult;
        const openApiJson = options?.openApiJson ?? state.openApiJson;

        if (!mapping) {
          set({ error: "Необходимо выполнить маппинг перед генерацией данных" });
          return;
        }

        if (!openApiJson) {
          set({ error: "Необходим OpenAPI для генерации данных" });
          return;
        }

        set({ loading: true, error: null });

        try {
          const request = {
            generationType: (options?.generationType ?? "CLASSIC") as "CLASSIC" | "AI",
            mappingResult: mapping,
            openApiJson: openApiJson,
            scenario: options?.scenario ?? "positive",
            variantsCount: options?.variantsCount ?? 1,
          };

          const result = await generateTestData(request);

          // Преобразуем результат в формат TestDataTemplate
          const templates: TestDataTemplate[] = result.variants.map((variant, index) => {
            const contexts: TestDataContext[] = [];

            // Глобальный контекст с общими данными
            const globalFields: TestDataField[] = [];
            if (result.crossStepDependencies) {
              for (const [key, value] of Object.entries(result.crossStepDependencies)) {
                const [stepId, fieldName] = key.split(".");
                globalFields.push({
                  key: `${stepId}_${fieldName}`,
                  label: `${stepId}.${fieldName}`,
                  type: "string",
                  value: value,
                  dependsOn: { stepId, field: fieldName },
                  editable: true,
                } as any);
              }
            }

            if (globalFields.length > 0) {
              contexts.push({
                id: crypto.randomUUID(),
                scope: "global",
                label: "Глобальные данные",
                fields: globalFields,
              } as any);
            }

            // Контексты для каждого шага
            for (const stepData of variant) {
              const fields: TestDataField[] = [];

              if (stepData.requestData) {
                fields.push({
                  key: "requestData",
                  label: "Данные запроса",
                  type: "json",
                  value: stepData.requestData,
                  editable: true,
                } as any);
              }

              if (stepData.queryParams) {
                fields.push({
                  key: "queryParams",
                  label: "Query параметры",
                  type: "json",
                  value: stepData.queryParams,
                  editable: true,
                } as any);
              }

              if (stepData.responseData) {
                fields.push({
                  key: "responseData",
                  label: "Ожидаемый ответ",
                  type: "json",
                  value: stepData.responseData,
                  editable: true,
                } as any);
              }

              if (stepData.dataDependencies) {
                for (const [fieldName, targetStepId] of Object.entries(stepData.dataDependencies)) {
                  fields.push({
                    key: `dep_${fieldName}`,
                    label: `Зависимость: ${fieldName}`,
                    type: "string",
                    value: "",
                    dependsOn: { stepId: targetStepId, field: fieldName },
                    editable: true,
                  } as any);
                }
              }

              if (fields.length > 0) {
                contexts.push({
                  id: crypto.randomUUID(),
                  scope: "step",
                  label: stepData.taskName,
                  relatedStepId: stepData.taskId,
                  fields,
                } as any);
              }
            }

            return {
              id: crypto.randomUUID(),
              name: `Вариант ${index + 1} (${result.scenario})`,
              seed: result.statistics?.generationTimeMs?.toString() ?? String(Date.now()),
              contexts,
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            } as TestDataTemplate;
          });

          set({
            templates,
            activeTemplateId: templates[0]?.id ?? null,
            loading: false,
          });
        } catch (error) {
          set({
            error:
              error instanceof Error
                ? error.message
                : "Не удалось сгенерировать тестовые данные",
            loading: false,
          });
        }
      },

      setActiveTemplate: (templateId) => {
        set({ activeTemplateId: templateId });
      },

      setGlobalOverride: (stepId, field, value) => {
        set((state) => {
          const key = `${stepId}.${field}`;
          return {
            globalOverrides: { ...(state.globalOverrides ?? {}), [key]: value },
          } as any;
        });
      },

      setCommonOverride: (fieldName, value) => {
        set((state) => ({
          commonOverrides: { ...(state.commonOverrides ?? {}), [fieldName]: value },
        } as any));
      },

      loadAiModels: async () => {
        try {
          const models = await fetchAiModels();
          set((state) => ({
            ...state,
            aiModels: models,
            selectedModelId: models.length ? models[0].id : null,
          } as any));
        } catch (_) {}
      },

      setSelectedModelId: (modelId) => {
        set((state) => ({
          ...state,
          selectedModelId: modelId,
        } as any));
      },

        setSelectedScenario: (scenarioId) => {
          set({ selectedScenarioId: scenarioId });
        },

        importArtifacts: async (files) => {
          if (!files.length) return;

          const bpmn = files.find(
            (f) => f.name.endsWith(".bpmn") || f.name.endsWith(".xml"),
          );
          const openapi = files.find(
            (f) =>
              f.name.endsWith(".json") ||
              f.name.endsWith(".yaml") ||
              f.name.endsWith(".yml"),
          );

          if (!bpmn || !openapi) {
            set({
              error: "Загрузите BPMN (.bpmn/.xml) и OpenAPI (.json/.yaml)",
            });
            return;
          }

          set({ loading: true, error: null });
          const now = new Date().toISOString();
          const newArtifacts = files.map((f) => ({
            id: crypto.randomUUID(),
            name: f.name,
            type: f.name.endsWith(".bpmn") || f.name.endsWith(".xml") ? "bpmn" : "openapi",
            status: "processing",
            uploadedAt: now,
            source: { kind: "upload" },
            progress: 10,
          } as any));
          set((state) => ({ artifacts: [...newArtifacts, ...state.artifacts] }));
          try {
            const openApiContent = await decodeFileSmart(openapi);
            const bpmnContent = await decodeFileSmart(bpmn);
            const payload = {
              bpmnXml: bpmnContent,
              openApiJson: openApiContent,
            } satisfies MappingPayload;

            const mapping = await requestMapping(payload);
            const scenario = mappingToScenario(mapping);
            const { nodes, edges } = mappingToProcessGraph(mapping);
            const issues = mappingToAnalysisIssues(mapping);

            set({
              scenarios: [scenario],
              selectedScenarioId: scenario.id,
              processNodes: nodes,
              processEdges: edges,
              analysisIssues: issues,
              mappingResult: mapping,
              openApiJson: openApiContent,
              bpmnXml: bpmnContent,
              loading: false,
            });
            set((state) => ({
              artifacts: state.artifacts.map((a) => ({
                ...a,
                status: "ready",
                progress: 100,
                summary: a.type === "bpmn"
                  ? { tasks: mapping.totalTasks, warnings: issues.filter((i) => i.severity !== "info").length }
                  : { endpoints: mapping.totalEndpoints, warnings: issues.filter((i) => i.severity !== "info").length },
              })),
            }));

            try {
              const modelId = (get() as any).selectedModelId as number | null;
              const { jobId } = await startAiVerification(payload, modelId ?? undefined);
              const poll = async () => {
                try {
                  const status = await getAiStatus(jobId);
                  if (status.status === "completed" && status.result) {
                    set((state) => ({
                      mappingResult: {
                        ...(state.mappingResult as any),
                        aiVerificationReport: status.result,
                      },
                    }));
                    clearInterval(timer);
                  } else if (status.status === "error") {
                    clearInterval(timer);
                  }
                } catch (_) {
                  // ignore transient errors
                }
              };
              const timer = setInterval(poll, 2000);
            } catch (_) {
              // ignore AI start errors
            }
          } catch (error) {
            set({
              error:
                error instanceof Error
                  ? error.message
                  : "Не удалось импортировать артефакты",
              loading: false,
            });
          }
        },

        runMapping: async (payload, options) => {
          set({ loading: true, error: null });
          try {
            const mapping = await requestMapping(payload);
            const scenario = mappingToScenario(mapping);
            if (options?.scenarioName) {
              scenario.name = options.scenarioName;
            }
            const { nodes, edges } = mappingToProcessGraph(mapping);
            const issues = mappingToAnalysisIssues(mapping);

            set({
              scenarios: [scenario],
              selectedScenarioId: scenario.id,
              processNodes: nodes,
              processEdges: edges,
              analysisIssues: issues,
              mappingResult: mapping,
              openApiJson: payload.openApiJson,
              loading: false,
            });

            try {
              const modelId = (get() as any).selectedModelId as number | null;
              const { jobId } = await startAiVerification(payload, modelId ?? undefined);
              const poll = async () => {
                try {
                  const status = await getAiStatus(jobId);
                  if (status.status === "completed" && status.result) {
                    set((state) => ({
                      mappingResult: {
                        ...(state.mappingResult as any),
                        aiVerificationReport: status.result,
                      },
                    }));
                    clearInterval(timer);
                  } else if (status.status === "error") {
                    clearInterval(timer);
                  }
                } catch (_) {}
              };
              const timer = setInterval(poll, 2000);
            } catch (_) {}
          } catch (error) {
            set({
              error:
                error instanceof Error
                  ? error.message
                  : "Не удалось выполнить сопоставление",
              loading: false,
            });
          }
        },

        startRun: async (scenarioId, options) => {
          const scenario = get().scenarios.find((s) => s.id === scenarioId);

          if (!scenario) throw new Error("Сценарий не найден");

          set({ loading: true, error: null });

          try {
            const execution = await triggerScenarioRun({
              scenarioId,
              parallelism: options?.parallelism ?? 1,
              dataTemplateId: options?.templateId,
            });

            set((state) => ({
              runnerExecutions: [execution, ...state.runnerExecutions],
              selectedExecutionId: execution.id,
              loading: false,
            }));
          } catch (error) {
            set({
              error:
                error instanceof Error ? error.message : "Ошибка запуска прогона",
              loading: false,
            });
          }
        },

      addScenarioStep: (scenarioId, stepPayload) => {
        set((state) => {
          const scenarioIndex = state.scenarios.findIndex(
            (scenario) => scenario.id === scenarioId,
          );

          if (scenarioIndex === -1) {
            return state;
          }

          const scenario = state.scenarios[scenarioIndex];
          const newStepId = crypto.randomUUID();
          const order = scenario.steps.length + 1;
          const expectedStatus =
            stepPayload.expectedStatus ?? getDefaultStatus(stepPayload.method);

          const newStep = {
            id: newStepId,
            order,
            title: stepPayload.title,
            description: `Пользовательский шаг #${order}`,
            endpoint: stepPayload.endpoint,
            method: stepPayload.method,
            expectedStatus,
            payload: {},
            preconditions: [],
            outputs: [],
            timeoutMs: 20000,
            retries: { maxAttempts: 2, delayMs: 2000 },
            status: "pending" as StepExecutionStatus,
          };

          const newNode: ProcessNode = {
            id: newStepId,
            label: stepPayload.title,
            type: "api",
            position: getGridPosition(state.processNodes.length),
            status: "pending",
            metadata: {
              endpoint: stepPayload.endpoint,
              method: stepPayload.method,
            },
          };

          const updatedScenarios = [...state.scenarios];
          updatedScenarios[scenarioIndex] = {
            ...scenario,
            steps: [...scenario.steps, newStep],
            updatedAt: new Date().toISOString(),
          };

          return {
            scenarios: updatedScenarios,
            processNodes: [...state.processNodes, newNode],
          };
        });
      },

      connectScenarioSteps: (scenarioId, sourceStepId, targetStepId) => {
        if (sourceStepId === targetStepId) return;

        set((state) => {
          const scenarioIndex = state.scenarios.findIndex(
            (scenario) => scenario.id === scenarioId,
          );

          if (scenarioIndex === -1) {
            return state;
          }

          const scenario = state.scenarios[scenarioIndex];
          const edgeExists = state.processEdges.some(
            (edge) => edge.from === sourceStepId && edge.to === targetStepId,
          );

          const processEdges = edgeExists
            ? state.processEdges
            : [
                ...state.processEdges,
                {
                  id: crypto.randomUUID(),
                  from: sourceStepId,
                  to: targetStepId,
                },
              ];

          const updatedSteps = scenario.steps.map((step) => {
            if (step.id === targetStepId) {
              const preconditions = step.preconditions ?? [];
              if (!preconditions.includes(sourceStepId)) {
                return {
                  ...step,
                  preconditions: [...preconditions, sourceStepId],
                };
              }
            }

            if (step.id === sourceStepId) {
              const outputs = step.outputs ?? [];
              if (!outputs.includes(targetStepId)) {
                return {
                  ...step,
                  outputs: [...outputs, targetStepId],
                };
              }
            }

            return step;
          });

          const updatedScenarios = [...state.scenarios];
          updatedScenarios[scenarioIndex] = {
            ...scenario,
            steps: updatedSteps,
            updatedAt: new Date().toISOString(),
          };

          return { scenarios: updatedScenarios, processEdges };
        });
      },

        setSelectedExecution: (executionId) => {
          set({ selectedExecutionId: executionId });
        },

        upsertExecution: (execution) => {
          set((state) => {
            const existingIndex = state.runnerExecutions.findIndex(
              (run) => run.id === execution.id,
            );

            if (existingIndex === -1) {
              return { runnerExecutions: [execution, ...state.runnerExecutions] };
            }
            const updatedExecutions = [...state.runnerExecutions];

            updatedExecutions[existingIndex] = execution;

            return { runnerExecutions: updatedExecutions };
          });
        },

        appendLog: (executionId, entry) => {
          set((state) => ({
            runnerExecutions: state.runnerExecutions.map((execution) =>
              execution.id === executionId
                ? { ...execution, logs: [...execution.logs, entry] }
                : execution,
            ),
          }));
        },

        updateNodeStatus: (stepId, status) => {
          set((state) => ({
            processNodes: state.processNodes.map((node) =>
              node.id === stepId ? { ...node, status } : node,
            ),
          }));
        },

        updateScenarioStepStatus: (scenarioId, stepId, status) => {
          set((state) => ({
            scenarios: state.scenarios.map((scenario) =>
              scenario.id === scenarioId
                ? {
                    ...scenario,
                    steps: scenario.steps.map((step) =>
                      step.id === stepId ? { ...step, status } : step,
                    ),
                  }
                : scenario,
            ),
          }));
        },

        updateProcessNodePosition: (nodeId, position) => {
          set((state) => ({
            processNodes: state.processNodes.map((node) =>
              node.id === nodeId ? { ...node, position } : node,
            ),
          }));
        },
      }),
  ),
);

const hasReplacementChars = (s: string): boolean => (s.match(/[\uFFFD]/g)?.length ?? 0) > 5;
const decodeFileSmart = async (file: File): Promise<string> => {
  const buffer = new Uint8Array(await file.arrayBuffer());
  const tryDecode = (enc: string) => {
    try {
      return new TextDecoder(enc, { fatal: false }).decode(buffer);
    } catch (_) {
      return "";
    }
  };
  let text = tryDecode("utf-8");
  if (!text || hasReplacementChars(text)) {
    for (const enc of ["windows-1251", "utf-16le", "utf-16be"]) {
      const alt = tryDecode(enc);
      if (alt && !hasReplacementChars(alt)) {
        text = alt;
        break;
      }
    }
  }
  return text || "";
};

