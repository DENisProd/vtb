import { Card, CardBody, CardHeader, Chip, Button } from "@heroui/react";
import { ArrowPathIcon } from "@heroicons/react/24/outline";
import { ProjectDto } from "@/lib/testflow-api";

interface ProjectRunnerTabProps {
  project: ProjectDto;
}

export function ProjectRunnerTab({ project }: ProjectRunnerTabProps) {
  return (
    <div className="space-y-4">
      <Card className="app-card">
        <CardHeader className="justify-between">
          <div>
            <div className="text-sm font-semibold text-[var(--app-text)]">
              Прогон тестов
            </div>
            <div className="text-xs text-muted">
              Поток логов и статус шагов в реальном времени
            </div>
          </div>
          <Button
            className="btn-primary"
            startContent={<ArrowPathIcon className="w-4 h-4" />}
          >
            Запустить прогон
          </Button>
        </CardHeader>
        <CardBody>
          <div className="space-y-2">
            <div className="text-sm text-muted">
              Логи прогона будут отображаться здесь
            </div>
            <div className="h-64 border border-white/10 rounded-lg p-4 bg-white/5 overflow-auto">
              <div className="text-xs font-mono space-y-1">
                <div className="text-success">[INFO] Инициализация прогона...</div>
                <div className="text-muted">[DEBUG] Загрузка сценариев...</div>
              </div>
            </div>
          </div>
        </CardBody>
      </Card>

      <Card className="app-card">
        <CardHeader>
          <div className="text-sm font-semibold text-[var(--app-text)]">
            Статус шагов
          </div>
        </CardHeader>
        <CardBody>
          <div className="space-y-2">
            <div className="flex items-center justify-between p-2 rounded bg-white/5">
              <div className="text-sm">Шаг 1: Инициализация</div>
              <Chip size="sm" color="success">Успешно</Chip>
            </div>
            <div className="flex items-center justify-between p-2 rounded bg-white/5">
              <div className="text-sm">Шаг 2: Запрос данных</div>
              <Chip size="sm" color="warning">В процессе</Chip>
            </div>
          </div>
        </CardBody>
      </Card>

      <Card className="app-card">
        <CardHeader>
          <div className="text-sm font-semibold text-[var(--app-text)]">
            Request / Response
          </div>
        </CardHeader>
        <CardBody>
          <div className="space-y-4">
            <div>
              <div className="text-xs font-semibold text-[var(--app-text)] mb-2">
                Request
              </div>
              <pre className="text-xs bg-white/5 p-3 rounded overflow-auto">
                {JSON.stringify({ method: "GET", url: "/api/endpoint" }, null, 2)}
              </pre>
            </div>
            <div>
              <div className="text-xs font-semibold text-[var(--app-text)] mb-2">
                Response
              </div>
              <pre className="text-xs bg-white/5 p-3 rounded overflow-auto">
                {JSON.stringify({ status: 200, data: {} }, null, 2)}
              </pre>
            </div>
          </div>
        </CardBody>
      </Card>
    </div>
  );
}

