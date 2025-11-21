import { Card, CardBody, CardHeader, Chip, Button } from "@heroui/react";
import {
  PlusIcon,
  PlayIcon,
  PencilIcon,
  DocumentDuplicateIcon,
  BoltIcon,
} from "@heroicons/react/24/outline";
import { ProjectDto } from "@/lib/testflow-api";

interface ProjectScenariosTabProps {
  project: ProjectDto;
}

export function ProjectScenariosTab({ project }: ProjectScenariosTabProps) {
  const mappingResult = project.mappingResult;
  const scenarios = mappingResult
    ? Object.entries(mappingResult.taskMappings ?? {}).map(([taskId, mapping]) => ({
        id: taskId,
        name: mapping.taskName,
        steps: 1,
        duration: "~1 сек",
        errors: 0,
        artifacts: [
          project.bpmnXml && "BPMN",
          project.openApiJson && "OpenAPI",
        ].filter(Boolean) as string[],
      }))
    : [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-lg font-semibold text-[var(--app-text)]">
            Сценарии тестирования
          </div>
          <div className="text-sm text-muted">
            {scenarios.length} сценариев в проекте
          </div>
        </div>
        <div className="flex gap-2">
          <Button
            className="btn-primary"
            startContent={<PlusIcon className="w-4 h-4" />}
          >
            Создать сценарий
          </Button>
          <Button variant="bordered" startContent={<BoltIcon className="w-4 h-4" />}>
            Сгенерировать автоматом
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {scenarios.map((scenario) => (
          <Card
            key={scenario.id}
            className="app-card hover:border-[var(--app-primary)]/50 transition-colors"
          >
            <CardHeader>
              <div className="flex-1">
                <div className="text-sm font-semibold text-[var(--app-text)] break-words">
                  {scenario.name}
                </div>
                <div className="flex items-center gap-2 mt-2">
                  <Chip size="sm" variant="flat">
                    ✔ {scenario.steps} шагов
                  </Chip>
                  <Chip size="sm" variant="flat">
                    ⏱ {scenario.duration}
                  </Chip>
                  {scenario.errors > 0 && (
                    <Chip size="sm" variant="flat" color="danger">
                      {scenario.errors} ошибка
                    </Chip>
                  )}
                </div>
              </div>
            </CardHeader>
            <CardBody>
              <div className="flex flex-wrap gap-1 mb-3 break-words">
                {scenario.artifacts.map((artifact) => (
                  <Chip key={artifact} size="sm" variant="flat">
                    {artifact}
                  </Chip>
                ))}
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="light" startContent={<PlayIcon className="w-4 h-4" />}>
                  Запустить
                </Button>
                <Button
                  size="sm"
                  variant="light"
                  isIconOnly
                  startContent={<PencilIcon className="w-4 h-4" />}
                />
                <Button
                  size="sm"
                  variant="light"
                  isIconOnly
                  startContent={<DocumentDuplicateIcon className="w-4 h-4" />}
                />
              </div>
            </CardBody>
          </Card>
        ))}
      </div>

      {scenarios.length === 0 && (
        <Card className="app-card">
          <CardBody className="text-center py-12">
            <div className="text-muted">
              Нет сценариев. Создайте или сгенерируйте сценарий.
            </div>
          </CardBody>
        </Card>
      )}
    </div>
  );
}

