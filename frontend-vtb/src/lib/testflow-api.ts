import type {
  MappingPayload,
  MappingResultDto,
  RunnerExecution,
} from "@/types/testflow";

const DEFAULT_BASE_URL = "http://localhost:8080";

const getApiBaseUrl = () => {
  const envUrl = import.meta.env.VITE_API_BASE_URL;

  return (envUrl ?? DEFAULT_BASE_URL).replace(/\/$/, "");
};

export async function requestMapping(
  payload: MappingPayload,
): Promise<MappingResultDto> {
  const formData = new FormData();

  formData.append("bpmnXml", payload.bpmnXml);
  formData.append("openApiJson", payload.openApiJson);

  const response = await fetch(`${getApiBaseUrl()}/api/mapping/map`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const errorBody = await response.text();

    throw new Error(
      `Mapping request failed (${response.status}): ${errorBody || response.statusText}`,
    );
  }

  return (await response.json()) as MappingResultDto;
}

export interface TriggerRunPayload {
  scenarioId?: string;
  projectId?: string;
  parallelism?: number;
  dataTemplateId?: string;
  dryRun?: boolean;
}

export async function triggerScenarioRun(
  payload: TriggerRunPayload,
): Promise<RunnerExecution> {
  const response = await fetch(`${getApiBaseUrl()}/api/runner/run`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(`Run trigger failed (${response.status})`);
  }

  return (await response.json()) as RunnerExecution;
}

export async function fetchRunnerExecution(
  executionId: string,
): Promise<RunnerExecution> {
  const response = await fetch(`${getApiBaseUrl()}/api/runner/${executionId}`);

  if (!response.ok) {
    throw new Error(`Unable to fetch runner execution ${executionId}`);
  }

  return (await response.json()) as RunnerExecution;
}

export async function getRunnerHistory(
  projectId?: string,
): Promise<RunnerExecution[]> {
  const url = projectId
    ? `${getApiBaseUrl()}/api/runner/history?projectId=${encodeURIComponent(projectId)}`
    : `${getApiBaseUrl()}/api/runner/history`;
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error(`Unable to fetch runner history`);
  }

  return (await response.json()) as RunnerExecution[];
}

export type RunnerLogCallback = (event: MessageEvent) => void;

export const subscribeToRunnerLogs = (
  executionId: string,
  handler: RunnerLogCallback,
): (() => void) => {
  const base = getApiBaseUrl().replace(/^http/, "ws");
  const url = `${base}/ws/runner/${executionId}`;
  const ws = new WebSocket(url);

  ws.addEventListener("message", handler);

  return () => {
    ws.removeEventListener("message", handler);
    ws.close();
  };
};

export async function startAiVerification(
  payload: MappingPayload,
  modelId?: number,
  projectId?: string,
): Promise<{ jobId: string }> {
  const formData = new FormData();
  formData.append("bpmnXml", payload.bpmnXml);
  formData.append("openApiJson", payload.openApiJson);
  if (typeof modelId === "number") {
    formData.append("modelId", String(modelId));
  }
  if (projectId) {
    formData.append("projectId", projectId);
  }
  const response = await fetch(`${getApiBaseUrl()}/api/ai/verify`, {
    method: "POST",
    body: formData,
  });
  if (!response.ok) {
    throw new Error(`AI verification start failed (${response.status})`);
  }
  return (await response.json()) as { jobId: string };
}

export async function getAiStatus(jobId: string): Promise<{
  status: "queued" | "running" | "completed" | "error";
  result?: MappingResultDto["aiVerificationReport"];
  error?: string;
  createdAt?: string;
  startedAt?: string;
  finishedAt?: string;
  modelName?: string;
  projectId?: string;
}> {
  const response = await fetch(`${getApiBaseUrl()}/api/ai/status/${jobId}`);
  if (!response.ok) {
    throw new Error(`AI status request failed (${response.status})`);
  }
  return (await response.json()) as any;
}

export async function listAiJobs(projectId: string): Promise<{
  jobs: Array<{ id: string; status: string; createdAt?: string; startedAt?: string; finishedAt?: string; modelName?: string; result?: MappingResultDto["aiVerificationReport"]; error?: string }>;
}> {
  const response = await fetch(`${getApiBaseUrl()}/api/ai/jobs?projectId=${encodeURIComponent(projectId)}`);
  if (!response.ok) {
    throw new Error(`AI jobs list request failed (${response.status})`);
  }
  return (await response.json()) as any;
}

export async function fetchAiModels(): Promise<{ id: number; name: string }[]> {
  const response = await fetch(`${getApiBaseUrl()}/api/ai/models`);
  if (!response.ok) {
    throw new Error(`AI models request failed (${response.status})`);
  }
  const data = (await response.json()) as { models: { id: number; name: string }[] };
  return data.models;
}

export interface ProjectDto {
  id: string;
  name: string;
  bpmnXml: string;
  openApiJson: string;
  pumlContent?: string | null;
  mappingResult?: MappingResultDto;
}

export async function listProjects(): Promise<ProjectDto[]> {
  const response = await fetch(`${getApiBaseUrl()}/api/projects`);
  if (!response.ok) {
    throw new Error(`Projects list request failed (${response.status})`);
  }
  return (await response.json()) as ProjectDto[];
}

export async function getProject(id: string): Promise<ProjectDto> {
  const response = await fetch(`${getApiBaseUrl()}/api/projects/${id}`);
  if (!response.ok) {
    throw new Error(`Project request failed (${response.status})`);
  }
  return (await response.json()) as ProjectDto;
}

export async function remapProject(
  id: string,
  payload?: { bpmnXml?: string; openApiJson?: string; pumlContent?: string },
): Promise<ProjectDto> {
  const formData = new FormData();
  if (payload?.bpmnXml) formData.append("bpmnXml", payload.bpmnXml);
  if (payload?.openApiJson) formData.append("openApiJson", payload.openApiJson);
  if (payload?.pumlContent) formData.append("pumlContent", payload.pumlContent);
  const response = await fetch(`${getApiBaseUrl()}/api/projects/${id}/remap`, {
    method: "POST",
    body: formData,
  });
  if (!response.ok) {
    throw new Error(`Project remap failed (${response.status})`);
  }
  return (await response.json()) as ProjectDto;
}

export async function createProject(
  name: string,
  bpmnXml: string,
  openApiJson: string,
  pumlContent?: string,
): Promise<ProjectDto> {
  const formData = new FormData();
  formData.append("name", name);
  formData.append("bpmnXml", bpmnXml);
  formData.append("openApiJson", openApiJson);
  if (pumlContent) formData.append("pumlContent", pumlContent);

  const response = await fetch(`${getApiBaseUrl()}/api/projects`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Project create failed (${response.status}): ${text || response.statusText}`);
  }

  return (await response.json()) as ProjectDto;
}

