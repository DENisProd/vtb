import { Card, CardBody, CardHeader, Chip } from "@heroui/react";
import {
  ExclamationTriangleIcon,
  DocumentTextIcon,
  Cog6ToothIcon,
} from "@heroicons/react/24/outline";
import { ProjectDto } from "@/lib/testflow-api";

interface ProjectContextPanelProps {
  project: ProjectDto;
}

export function ProjectContextPanel({ project }: ProjectContextPanelProps) {
  const errors = project.mappingResult
    ? [
        ...(project.mappingResult.unmatchedTasks ?? []),
        ...(project.mappingResult.aiVerificationReport?.openapi?.errors ?? []),
        ...(project.mappingResult.aiVerificationReport?.bpmn?.errors ?? []),
      ]
    : [];

  const warnings = project.mappingResult
    ? [
        ...(project.mappingResult.aiVerificationReport?.openapi?.warnings ?? []),
        ...(project.mappingResult.aiVerificationReport?.bpmn?.warnings ?? []),
      ]
    : [];

  const artifactCount = [
    project.bpmnXml && "BPMN",
    project.openApiJson && "OpenAPI",
    project.pumlContent && "PUML",
  ].filter(Boolean).length;

  return (
    <aside className="w-full lg:w-64 shrink-0 flex flex-col gap-3 min-h-0">
      {/* Ошибки - приоритетная информация */}
      <Card className="app-card shrink-0">
        <CardHeader className="flex items-center gap-2 pb-2">
          <ExclamationTriangleIcon className="w-4 h-4 text-danger shrink-0" />
          <div className="flex-1 min-w-0">
            <div className="text-xs font-semibold text-[var(--app-text)] truncate">Ошибки</div>
            <div className="text-[10px] text-muted">
              {errors.length > 0 ? `${errors.length} найдено` : "Нет ошибок"}
            </div>
          </div>
          {errors.length > 0 && (
            <Chip size="sm" variant="flat" color="danger" className="shrink-0">
              {errors.length}
            </Chip>
          )}
        </CardHeader>
        <CardBody className="pt-0 space-y-1.5 max-h-48 overflow-y-auto">
          {errors.length === 0 ? (
            <div className="text-[10px] text-muted py-2">✓ Все проверки пройдены</div>
          ) : (
            errors.slice(0, 4).map((error, idx) => (
              <div
                key={idx}
                className="rounded border border-danger/30 bg-danger/5 p-1.5 text-[10px] leading-relaxed"
              >
                <div className="font-medium text-danger mb-0.5">
                  {typeof error === "string" ? "Ошибка" : error.elementName || "Ошибка"}
                </div>
                <div className="text-muted text-[9px] line-clamp-2">
                  {typeof error === "string" ? error : error.recommendations?.[0] || "Детали недоступны"}
                </div>
              </div>
            ))
          )}
        </CardBody>
      </Card>

      {/* Статус анализа - важная информация */}
      {project.mappingResult && (
        <Card className="app-card shrink-0">
          <CardHeader className="flex items-center gap-2 pb-2">
            <Cog6ToothIcon className="w-4 h-4 text-[var(--app-primary)] shrink-0" />
            <div className="flex-1 min-w-0">
              <div className="text-xs font-semibold text-[var(--app-text)]">Статус анализа</div>
            </div>
          </CardHeader>
          <CardBody className="pt-0 space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="text-muted">Уверенность:</span>
              <Chip 
                size="sm" 
                variant="flat" 
                color={
                  (project.mappingResult.overallConfidence ?? 0) > 0.8 ? "success" :
                  (project.mappingResult.overallConfidence ?? 0) > 0.5 ? "warning" : "danger"
                }
              >
                {Math.round((project.mappingResult.overallConfidence ?? 0) * 100)}%
              </Chip>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-muted">Сопоставлено:</span>
              <span className="font-semibold text-[var(--app-text)]">
                {project.mappingResult.matchedTasks} / {project.mappingResult.totalTasks}
              </span>
            </div>
            <div className="flex items-center justify-between text-xs">
              <span className="text-muted">Эндпоинты:</span>
              <span className="font-semibold text-[var(--app-text)]">
                {project.mappingResult.matchedEndpoints} / {project.mappingResult.totalEndpoints}
              </span>
            </div>
          </CardBody>
        </Card>
      )}

      {/* Артефакты - компактно */}
      <Card className="app-card shrink-0">
        <CardHeader className="flex items-center gap-2 pb-2">
          <DocumentTextIcon className="w-4 h-4 text-[var(--app-primary)] shrink-0" />
          <div className="flex-1 min-w-0">
            <div className="text-xs font-semibold text-[var(--app-text)]">Артефакты</div>
            <div className="text-[10px] text-muted">{artifactCount} загружено</div>
          </div>
        </CardHeader>
        <CardBody className="pt-0">
          <div className="flex flex-wrap gap-1.5">
            {project.bpmnXml && (
              <Chip size="sm" variant="flat" color="primary">BPMN</Chip>
            )}
            {project.openApiJson && (
              <Chip size="sm" variant="flat" color="primary">OpenAPI</Chip>
            )}
            {project.pumlContent && (
              <Chip size="sm" variant="flat" color="primary">PUML</Chip>
            )}
            {artifactCount === 0 && (
              <div className="text-[10px] text-muted">Нет артефактов</div>
            )}
          </div>
        </CardBody>
      </Card>
    </aside>
  );
}

