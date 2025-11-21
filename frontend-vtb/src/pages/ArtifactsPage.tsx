import { ChangeEvent, useState } from "react";
import { Button, Card, CardBody, CardHeader, Chip, Input } from "@heroui/react";
import {
  ArrowUpOnSquareIcon,
  CloudArrowUpIcon,
  DocumentTextIcon,
  LinkIcon,
} from "@heroicons/react/24/outline";

import { useTestFlowStore } from "@/stores/testflow-store";

const ArtifactsPage = () => {
  const artifacts = useTestFlowStore((state) => state.artifacts);
  const importArtifacts = useTestFlowStore((state) => state.importArtifacts);
  const [repoUrl, setRepoUrl] = useState("");

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    if (!event.target.files) return;
    importArtifacts(Array.from(event.target.files));
  };

  return (
    <div className="space-y-6">
      <Card className="border border-primary/20 bg-primary/5">
        <CardHeader className="items-start gap-3">
          <div className="rounded-full bg-primary/10 p-2">
            <CloudArrowUpIcon className="h-5 w-5 text-primary" />
          </div>
          <div>
            <h1 className="text-xl font-semibold text-white">
              Импорт артефактов
            </h1>
            <p className="text-sm text-slate-400">
              Поддерживаются BPMN (*.bpmn/*.xml), OpenAPI (json/yaml), markdown
              и Postman коллекции.
            </p>
          </div>
        </CardHeader>
        <CardBody className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <label className="flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed border-primary/40 bg-white/10 p-6 text-center hover:border-primary">
            <input
              multiple
              className="hidden"
              type="file"
              onChange={handleFileChange}
            />
            <ArrowUpOnSquareIcon className="mb-2 h-8 w-8 text-primary" />
            <div className="font-semibold text-white">Перетащите файлы</div>
            <div className="text-xs text-slate-400">или нажмите, чтобы выбрать</div>
          </label>

          <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
            <div className="mb-2 text-sm font-semibold text-white">
              Подключить репозиторий
            </div>
            <Input
              placeholder="https://github.com/company/project"
              size="sm"
              startContent={<LinkIcon className="h-4 w-4 text-slate-500" />}
              value={repoUrl}
              onChange={(event) => setRepoUrl(event.target.value)}
            />
            <Button className="mt-3" color="secondary" variant="flat">
              Настроить CI импорт
            </Button>
          </div>

          <div className="space-y-2 rounded-2xl border border-white/10 bg-white/5 p-4">
            <div className="text-sm font-semibold text-white">
              Прогресс анализа
            </div>
            <div className="text-xs text-slate-500">
              распознанные задачи / эндпоинты
            </div>
            <div className="space-y-2">
              {artifacts.slice(0, 3).map((artifact) => (
                <div key={artifact.id} className="text-xs">
                  <div className="flex justify-between text-slate-300">
                    <span>{artifact.name}</span>
                    <span>
                      {artifact.summary?.tasks ??
                        artifact.summary?.endpoints ??
                        "—"}
                    </span>
                  </div>
                  <div className="mt-1 h-1.5 w-full rounded-full bg-white/10">
                    <div
                      className="h-1.5 rounded-full bg-primary"
                      style={{ width: `${artifact.progress ?? 100}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </CardBody>
      </Card>

      <Card className="border border-white/10">
        <CardHeader className="justify-between">
          <div>
            <div className="text-sm font-semibold text-white">Все артефакты</div>
            <div className="text-xs text-slate-500">
              управление версиями и связями
            </div>
          </div>
          <Button
            startContent={<DocumentTextIcon className="h-4 w-4" />}
            variant="light"
          >
            Экспорт списка
          </Button>
        </CardHeader>
        <CardBody className="space-y-3">
          {artifacts.map((artifact) => (
            <div
              key={artifact.id}
              className="rounded-2xl border border-white/10 bg-white/5 p-4"
            >
              <div className="flex flex-wrap items-center gap-2">
                <div className="text-sm font-semibold text-white">
                  {artifact.name}
                </div>
                <Chip size="sm" variant="flat">
                  {artifact.type}
                </Chip>
                <Chip
                  color={
                    artifact.status === "ready"
                      ? "success"
                      : artifact.status === "processing"
                        ? "warning"
                        : "danger"
                  }
                  size="sm"
                >
                  {artifact.status}
                </Chip>
                <div className="ml-auto text-xs text-slate-500">
                  {new Date(artifact.uploadedAt).toLocaleString()}
                </div>
              </div>
              <div className="mt-2 text-xs text-slate-400">
                {artifact.summary?.tasks && (
                  <span>{artifact.summary.tasks} шагов BPMN • </span>
                )}
                {artifact.summary?.endpoints && (
                  <span>{artifact.summary.endpoints} эндпоинтов • </span>
                )}
                {artifact.summary?.warnings && (
                  <span>{artifact.summary.warnings} предупреждений • </span>
                )}
                {artifact.summary?.sizeKB && (
                  <span>{artifact.summary.sizeKB} КБ</span>
                )}
              </div>
            </div>
          ))}
        </CardBody>
      </Card>
    </div>
  );
};

export default ArtifactsPage;

