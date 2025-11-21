import type {
  AnalysisIssue,
  MappingResultDto,
  ProcessEdge,
  ProcessNode,
  ScenarioStep,
  TestScenario,
} from "@/types/testflow";

const METHOD_DEFAULT_STATUS: Record<string, number> = {
  GET: 200,
  POST: 201,
  PUT: 200,
  PATCH: 200,
  DELETE: 204,
};

const inferStatus = (method: string) =>
  METHOD_DEFAULT_STATUS[method.toUpperCase()] ?? 200;

export const mappingToScenario = (mapping: MappingResultDto): TestScenario => {
  const steps: ScenarioStep[] = Object.values(mapping.taskMappings).map(
    (task, index) => {
      const expectedStatus = inferStatus(task.endpointMethod);
      const dataFlowEdges = mapping.dataFlowEdges || [];
      const preconditions = dataFlowEdges
        .filter((edge) => edge.targetTaskId === task.taskId)
        .map((edge) => edge.sourceTaskId);

      const outputs = dataFlowEdges
        .filter((edge) => edge.sourceTaskId === task.taskId)
        .flatMap((edge) => edge.fields ?? []);

      return {
        id: task.taskId,
        order: index + 1,
        title: task.taskName,
        description: task.recommendation ?? undefined,
        endpoint: task.endpointPath,
        method: task.endpointMethod,
        payload: task.customRequestData ?? undefined,
        expectedStatus,
        preconditions,
        outputs,
        timeoutMs: 20000,
        retries: {
          maxAttempts: 2,
          delayMs: 2000,
        },
        aiInsight: task.matchingStrategy
          ? `Совпадение по стратегии ${task.matchingStrategy}, уверенность ${(task.confidenceScore * 100).toFixed(0)}%`
          : undefined,
        status: "pending",
      };
    },
  );

  return {
    id: crypto.randomUUID(),
    name: "Автогенерированный сценарий",
    status: "draft",
    coverage: mapping.matchedTasks / Math.max(mapping.totalTasks, 1),
    owner: "AI Generator",
    riskLevel: mapping.overallConfidence > 0.8 ? "medium" : "high",
    tags: ["auto", "generated"],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    sourceArtifacts: [],
    steps,
  };
};

export const mappingToProcessGraph = (
  mapping: MappingResultDto,
): { nodes: ProcessNode[]; edges: ProcessEdge[] } => {
  const spacingX = 240;
  const spacingY = 160;

  const entries = Object.entries(mapping.taskMappings);
  const nodes: ProcessNode[] = entries.map(([taskId, task], index) => {
    const row = Math.floor(index / 3);
    const col = index % 3;

    return {
      id: taskId,
      label: task.taskName,
      type: "api",
      position: {
        x: col * spacingX + 120,
        y: row * spacingY + 120,
      },
      status: "pending",
      metadata: {
        endpoint: task.endpointPath,
        method: task.endpointMethod,
        confidence: task.confidenceScore,
      },
    };
  });

  const edges: ProcessEdge[] = (mapping.dataFlowEdges || []).map((edge, index) => ({
    id: `edge-${index}`,
    from: edge.sourceTaskId,
    to: edge.targetTaskId,
    label: edge.fields?.join(", "),
    fields: edge.fields,
    confidence: edge.confidence,
    parameterMappings: edge.parameterMappings
      ? Object.values(edge.parameterMappings).map((param) => ({
          parameterName: param.parameterName,
          parameterIn: param.parameterIn,
          sourceField: param.sourceField,
          fieldHint: param.fieldHint,
        }))
      : undefined,
  }));

  return { nodes, edges };
};

export const mappingToAnalysisIssues = (
  mapping: MappingResultDto,
): AnalysisIssue[] => {
  const issues: AnalysisIssue[] = [];

  if (mapping.aiVerificationReport?.openapi?.warnings?.length) {
    issues.push(
      ...mapping.aiVerificationReport.openapi.warnings.map((warning, index) => ({
        id: `openapi-warning-${index}`,
        category: "missing-validation" as const,
        severity: "warning" as const,
        confidence: 0.65,
        title: "OpenAPI предупреждение",
        details: warning,
        artifactId: "openapi",
        sourceRef: mapping.aiVerificationReport?.openapi?.summary,
        status: "open" as const,
        createdAt: new Date().toISOString(),
      })),
    );
  }

  if (mapping.aiVerificationReport?.bpmn?.warnings?.length) {
    issues.push(
      ...mapping.aiVerificationReport.bpmn.warnings.map((warning, index) => ({
        id: `bpmn-warning-${index}`,
        category: "ambiguous-text" as const,
        severity: "warning" as const,
        confidence: 0.55,
        title: "BPMN предупреждение",
        details: warning,
        artifactId: "bpmn",
        sourceRef: mapping.aiVerificationReport?.bpmn?.summary,
        status: "open" as const,
        createdAt: new Date().toISOString(),
      })),
    );
  }

  return issues;
};

