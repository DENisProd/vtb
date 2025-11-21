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
} from "@/types/testflow";
import { requestMapping, triggerScenarioRun, startAiVerification, getAiStatus, fetchAiModels } from "@/lib/testflow-api";
 
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
  generateTestDataTemplates: () => void;
  setActiveTemplate: (templateId: string) => void;
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

      generateTestDataTemplates: () => {
        const state = get();
        const mapping = state.mappingResult;
        const scenario = state.scenarios[0];
        const contexts: TestDataContext[] = [] as any;
        if (scenario) {
          for (const step of scenario.steps) {
            const fields: TestDataField[] = [] as any;
            if (step.payload && typeof step.payload === "object") {
              fields.push({
                key: "payload",
                label: "payload",
                type: "json",
                value: step.payload,
                editable: true,
              } as any);
            }
            if (Array.isArray(step.preconditions)) {
              for (const dep of step.preconditions) {
                fields.push({
                  key: `from_${dep}`,
                  label: `depends on ${dep}`,
                  type: "string",
                  value: "",
                  dependsOn: { stepId: dep, field: "result" },
                  confidence: 0.6,
                  reason: "Связь по dataFlow",
                  editable: true,
                } as any);
              }
            }
            contexts.push({
              id: crypto.randomUUID(),
              scope: "step",
              label: step.title,
              relatedStepId: step.id,
              fields,
            } as any);
          }
        }
        if (mapping) {
          const globalFields: TestDataField[] = [] as any;
          for (const item of mapping.secretFields ?? []) {
            globalFields.push({
              key: item.fieldName,
              label: item.fieldName,
              type: "string",
              value: "",
              confidence: 0.8,
              reason: item.description ?? "",
              editable: true,
            } as any);
          }
          for (const item of mapping.commonFields ?? []) {
            globalFields.push({
              key: item.fieldName,
              label: item.fieldName,
              type: "string",
              value: "",
              confidence: 0.5,
              reason: "Общий атрибут",
              editable: true,
            } as any);
          }
          if (globalFields.length) {
            contexts.unshift({
              id: crypto.randomUUID(),
              scope: "global",
              label: "Глобальные данные",
              fields: globalFields,
            } as any);
          }
        }
        const template: TestDataTemplate = {
          id: crypto.randomUUID(),
          name: "Default",
          seed: String(Date.now()),
          contexts,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        } as any;
        set({ templates: [template], activeTemplateId: template.id });
      },

      setActiveTemplate: (templateId) => {
        set({ activeTemplateId: templateId });
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
            const payload = {
              bpmnXml: await decodeFileSmart(bpmn),
              openApiJson: await decodeFileSmart(openapi),
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

