import { Card, CardBody, CardHeader, Button } from "@heroui/react";
import { BoltIcon } from "@heroicons/react/24/outline";
import { ProjectDto } from "@/lib/testflow-api";

interface ProjectTestDataTabProps {
  project: ProjectDto;
}

export function ProjectTestDataTab({ project }: ProjectTestDataTabProps) {
  return (
    <div className="space-y-4">
      <Card className="app-card">
        <CardHeader className="justify-between">
          <div>
            <div className="text-sm font-semibold text-[var(--app-text)]">
              Тестовые данные
            </div>
            <div className="text-xs text-muted">
              Автогенерация данных для тестирования
            </div>
          </div>
          <Button
            className="btn-primary"
            startContent={<BoltIcon className="w-4 h-4" />}
          >
            Сгенерировать данные
          </Button>
        </CardHeader>
        <CardBody>
          <div className="text-sm text-muted">
            Тестовые данные будут сгенерированы на основе OpenAPI схемы
          </div>
        </CardBody>
      </Card>

      <Card className="app-card">
        <CardHeader>
          <div className="text-sm font-semibold text-[var(--app-text)]">
            Предпросмотр JSON
          </div>
        </CardHeader>
        <CardBody>
          <pre className="text-xs bg-white/5 p-4 rounded-lg overflow-auto">
            {JSON.stringify({ example: "data" }, null, 2)}
          </pre>
        </CardBody>
      </Card>
    </div>
  );
}

