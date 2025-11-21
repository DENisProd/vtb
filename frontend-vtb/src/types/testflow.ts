export type ArtifactType = "bpmn" | "openapi" | "markdown" | "postman" | "text";

export type ArtifactStatus = "pending" | "processing" | "ready" | "error";

export interface ArtifactSummary {
  tasks?: number;
  endpoints?: number;
  warnings?: number;
  errors?: number;
  sizeKB?: number;
  durationMs?: number;
}

export interface ArtifactSource {
  kind: "upload" | "repository" | "ci" | "sample";
  reference?: string;
  branch?: string;
}

export interface Artifact {
  id: string;
  name: string;
  type: ArtifactType;
  status: ArtifactStatus;
  uploadedAt: string;
  source: ArtifactSource;
  summary?: ArtifactSummary;
  progress?: number;
  errorMessage?: string;
}

export type IssueCategory =
  | "inconsistency"
  | "missing-validation"
  | "failure-point"
  | "ambiguous-text"
  | "contract-drift";

export type IssueSeverity = "info" | "warning" | "error" | "critical";

export interface AnalysisIssue {
  id: string;
  category: IssueCategory;
  severity: IssueSeverity;
  confidence: number;
  title: string;
  details: string;
  artifactId?: string;
  sourceRef?: string;
  suggestedAction?: string;
  tags?: string[];
  assignedTo?: string;
  status: "open" | "acknowledged" | "resolved" | "dismissed";
  createdAt: string;
}

export type StepExecutionStatus =
  | "pending"
  | "running"
  | "success"
  | "warning"
  | "failed"
  | "skipped";

export interface ScenarioStep {
  id: string;
  order: number;
  title: string;
  description?: string;
  endpoint: string;
  method: string;
  payload?: Record<string, unknown>;
  expectedStatus?: number;
  expectedSchemaRef?: string;
  preconditions?: string[];
  outputs?: string[];
  timeoutMs?: number;
  retries?: {
    maxAttempts: number;
    delayMs: number;
  };
  aiInsight?: string;
  manual?: boolean;
  status: StepExecutionStatus;
}

export type ScenarioStatus = "draft" | "ready" | "approved" | "deprecated";

export interface TestScenario {
  id: string;
  name: string;
  status: ScenarioStatus;
  coverage: number;
  owner?: string;
  riskLevel: "low" | "medium" | "high";
  tags?: string[];
  createdAt: string;
  updatedAt: string;
  sourceArtifacts: string[];
  steps: ScenarioStep[];
}

export interface TestDataField {
  key: string;
  label: string;
  type: "string" | "number" | "boolean" | "json" | "uuid" | "date";
  value: unknown;
  dependsOn?: {
    stepId: string;
    field: string;
  };
  confidence?: number;
  reason?: string;
  editable?: boolean;
}

export interface TestDataContext {
  id: string;
  scope: "global" | "step";
  label: string;
  relatedStepId?: string;
  fields: TestDataField[];
}

export interface TestDataTemplate {
  id: string;
  name: string;
  seed?: string;
  contexts: TestDataContext[];
  createdAt: string;
  updatedAt: string;
}

export type RunnerStatus =
  | "idle"
  | "queued"
  | "running"
  | "paused"
  | "failed"
  | "completed";

export interface RunnerStepExecution {
  stepId: string;
  taskId?: string;
  taskName?: string;
  status: StepExecutionStatus;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  attempt?: number;
  errorMessage?: string;
  request?: {
    method: string;
    url: string;
    headers?: Record<string, string>;
    body?: string;
    timestamp?: string;
  };
  response?: {
    statusCode: number;
    headers?: Record<string, string>;
    body?: string;
    responseTimeMs?: number;
    timestamp?: string;
  };
}

export interface LogEntry {
  id: string;
  level: "debug" | "info" | "warn" | "error";
  message: string;
  timestamp: string;
  stepId?: string;
  payloadPreview?: Record<string, unknown>;
  responsePreview?: Record<string, unknown>;
}

export interface RunnerExecution {
  id: string;
  scenarioId?: string;
  projectId?: string;
  status: RunnerStatus;
  startedAt: string;
  finishedAt?: string;
  progress: number;
  parallelism: number;
  steps: RunnerStepExecution[];
  logs: LogEntry[];
  aiAnalysisJobId?: string;
  aiAnalysisResult?: string;
}

export interface ProcessNodePosition {
  x: number;
  y: number;
}

export type ProcessNodeType = "task" | "gateway" | "event" | "api" | "data";

export interface ProcessNode {
  id: string;
  label: string;
  type: ProcessNodeType;
  position: ProcessNodePosition;
  status: StepExecutionStatus;
  metadata?: Record<string, unknown>;
}

export interface ProcessEdge {
  id: string;
  from: string;
  to: string;
  label?: string;
  fields?: string[];
  confidence?: number;
  parameterMappings?: Array<{
    parameterName: string;
    parameterIn: string;
    sourceField: string;
    fieldHint?: string;
  }>;
}

export interface MappingPayload {
  bpmnXml: string;
  openApiJson: string;
}

export interface MappingResultDto {
  taskMappings: Record<
    string,
    {
      taskId: string;
      taskName: string;
      endpointPath: string;
      endpointMethod: string;
      operationId: string;
      confidenceScore: number;
      matchingStrategy?: string;
      recommendation?: string;
      customRequestData?: Record<string, unknown> | null;
    }
  >;
  dataFlowEdges?: Array<{
    sourceTaskId: string;
    targetTaskId: string;
    fields?: string[];
    confidence: number;
    parameterMappings?: Record<
      string,
      {
        parameterName: string;
        parameterIn: string;
        sourceField: string;
        fieldHint?: string;
      }
    >;
  }>;
  unmatchedTasks: Array<{
    elementId: string;
    elementName: string;
    elementType: string;
    recommendations: string[];
    maxConfidence: number;
  }>;
  overallConfidence: number;
  totalTasks: number;
  matchedTasks: number;
  totalEndpoints: number;
  matchedEndpoints: number;
  commonFields?: Array<{
    fieldName: string;
    fieldType: string;
    usageCount: number;
    usedInEndpoints: string[];
    required: boolean;
    description?: string | null;
    dataType?: string | null;
  }>;
  secretFields?: Array<{
    fieldName: string;
    fieldType: string;
    description?: string;
    dataType?: string;
    required?: boolean;
    usedInEndpoints?: string[];
    reason?: string;
  }>;
  aiVerificationReport?: {
    openapi?: {
      status: string;
      errors: string[];
      warnings: string[];
      suggestions: string[];
      summary?: string;
    };
    bpmn?: {
      status: string;
      errors: string[];
      warnings: string[];
      suggestions: string[];
      summary?: string;
    };
    overallStatus?: string;
    totalErrors?: number;
    totalWarnings?: number;
    totalSuggestions?: number;
  };
}

export interface TestFlowState {
  artifacts: Artifact[];
  analysisIssues: AnalysisIssue[];
  scenarios: TestScenario[];
  selectedScenarioId: string | null;
  templates: TestDataTemplate[];
  activeTemplateId: string | null;
  runnerExecutions: RunnerExecution[];
  selectedExecutionId: string | null;
  processNodes: ProcessNode[];
  processEdges: ProcessEdge[];
  mappingResult?: MappingResultDto;
  loading: boolean;
  error?: string | null;
  aiModels?: { id: number; name: string }[];
  selectedModelId?: number | null;
}

