import { Card, CardBody, CardHeader, Chip } from "@heroui/react";
import { ProjectDto } from "@/lib/testflow-api";

interface ProjectOverviewTabProps {
  project: ProjectDto;
}

export function ProjectOverviewTab({ project }: ProjectOverviewTabProps) {
  const mappingResult = project.mappingResult;
  const scenarioCount = mappingResult ? Object.keys(mappingResult.taskMappings ?? {}).length : 0;
  const confidence = mappingResult ? Math.round((mappingResult.overallConfidence ?? 0) * 100) : 0;
  const artifactCount = [
    project.bpmnXml && "BPMN",
    project.openApiJson && "OpenAPI",
    project.pumlContent && "PUML",
  ].filter(Boolean).length;

  return (
    <div className="space-y-4">
      {/* Основные метрики - крупные карточки */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <Card className="app-card">
          <CardHeader className="pb-2">
            <div className="text-xs font-semibold text-[var(--app-text)] uppercase tracking-wide">
              Статистика анализа
            </div>
          </CardHeader>
          <CardBody className="pt-0">
            {mappingResult ? (
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm text-muted">Задач</span>
                  <div className="text-right">
                    <div className="text-base font-semibold text-[var(--app-text)]">
                      {mappingResult.matchedTasks}
                    </div>
                    <div className="text-xs text-muted">из {mappingResult.totalTasks}</div>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-muted">Эндпоинтов</span>
                  <div className="text-right">
                    <div className="text-base font-semibold text-[var(--app-text)]">
                      {mappingResult.matchedEndpoints}
                    </div>
                    <div className="text-xs text-muted">из {mappingResult.totalEndpoints}</div>
                  </div>
                </div>
                <div className="pt-2 border-t border-white/10">
                  <div className="flex items-center justify-between">
                    <span className="text-sm text-muted">Уверенность</span>
                    <Chip 
                      size="sm" 
                      variant="flat" 
                      color={
                        confidence > 80 ? "success" :
                        confidence > 50 ? "warning" : "danger"
                      }
                    >
                      {confidence}%
                    </Chip>
                  </div>
                </div>
              </div>
            ) : (
              <div className="text-sm text-muted py-2">
                Анализ ещё не выполнен
              </div>
            )}
          </CardBody>
        </Card>

        <Card className="app-card">
          <CardHeader className="pb-2">
            <div className="text-xs font-semibold text-[var(--app-text)] uppercase tracking-wide">
              Артефакты
            </div>
          </CardHeader>
          <CardBody className="pt-0">
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted">Загружено</span>
                <Chip size="sm" variant="flat" color="primary">
                  {artifactCount}
                </Chip>
              </div>
              <div className="flex flex-wrap gap-2 pt-2">
                {project.bpmnXml && (
                  <Chip size="sm" variant="flat" color="success">✓ BPMN</Chip>
                )}
                {project.openApiJson && (
                  <Chip size="sm" variant="flat" color="success">✓ OpenAPI</Chip>
                )}
                {project.pumlContent && (
                  <Chip size="sm" variant="flat" color="success">✓ PUML</Chip>
                )}
                {artifactCount === 0 && (
                  <div className="text-xs text-muted">Нет артефактов</div>
                )}
              </div>
            </div>
          </CardBody>
        </Card>

        <Card className="app-card">
          <CardHeader className="pb-2">
            <div className="text-xs font-semibold text-[var(--app-text)] uppercase tracking-wide">
              Сценарии
            </div>
          </CardHeader>
          <CardBody className="pt-0">
            {mappingResult ? (
              <div className="space-y-2">
                <div className="text-2xl font-bold text-[var(--app-text)]">
                  {scenarioCount}
                </div>
                <div className="text-xs text-muted">
                  {scenarioCount === 0 
                    ? "Сценарии не созданы" 
                    : `${scenarioCount} ${scenarioCount === 1 ? "сценарий" : "сценариев"} создано`}
                </div>
              </div>
            ) : (
              <div className="text-sm text-muted py-2">
                Выполните анализ для создания сценариев
              </div>
            )}
          </CardBody>
        </Card>
      </div>

      {/* Дополнительная информация */}
      {mappingResult && scenarioCount > 0 && (
        <Card className="app-card">
          <CardHeader className="pb-2">
            <div className="text-xs font-semibold text-[var(--app-text)] uppercase tracking-wide">
              Детали сценариев
            </div>
          </CardHeader>
          <CardBody className="pt-0">
            <div className="text-sm text-muted">
              Перейдите на вкладку "Сценарии" для просмотра и редактирования созданных сценариев тестирования.
            </div>
          </CardBody>
        </Card>
      )}
    </div>
  );
}

