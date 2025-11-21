import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, CardBody, CardFooter, CardHeader, Chip, Input, Select, SelectItem, Modal, ModalContent, ModalHeader, ModalBody, ModalFooter } from "@heroui/react";
import { ArrowPathIcon, FolderOpenIcon, BellIcon, StarIcon } from "@heroicons/react/24/outline";
import { listProjects, remapProject, createProject, type ProjectDto } from "@/lib/testflow-api";

const ProjectsPage = () => {
  const [projects, setProjects] = useState<ProjectDto[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const [q, setQ] = useState("");
  const [filterArtifact, setFilterArtifact] = useState<string | null>(null);
  const [filterStatus, setFilterStatus] = useState<string | null>(null);
  const [favorites, setFavorites] = useState<Record<string, boolean>>({});
  const [openCreate, setOpenCreate] = useState(false);
  const [newProjectName, setNewProjectName] = useState("");

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const items = await listProjects();
        setProjects(items);
        if (!activeId && items.length > 0) setActiveId(items[0].id);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Не удалось загрузить проекты");
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [activeId]);

  const active = projects.find((p) => p.id === activeId) ?? null;
  const filtered = useMemo(() => {
    return projects.filter((p) => {
      const matchesQ = q.trim() ? p.name.toLowerCase().includes(q.toLowerCase()) : true;
      const hasBpmn = !!p.bpmnXml;
      const hasOpenApi = !!p.openApiJson;
      const hasPuml = !!p.pumlContent;
      const artifactOk = !filterArtifact ||
        (filterArtifact === "bpmn" && hasBpmn) ||
        (filterArtifact === "openapi" && hasOpenApi) ||
        (filterArtifact === "puml" && hasPuml);
      const warnings = p.mappingResult?.aiVerificationReport?.totalWarnings ?? 0;
      const status = warnings > 0 ? "warn" : p.mappingResult ? "ok" : "pending";
      const statusOk = !filterStatus || filterStatus === status;
      return matchesQ && artifactOk && statusOk;
    });
  }, [projects, q, filterArtifact, filterStatus]);

  const handleRemap = async () => {
    if (!active) return;
    setLoading(true);
    setError(null);
    try {
      const updated = await remapProject(active.id);
      setProjects((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось выполнить ремаппинг");
    } finally {
      setLoading(false);
    }
  };

  const handleCreateProject = async () => {
    const name = newProjectName.trim();
    if (!name) return;
    setLoading(true);
    setError(null);
    try {
      const created = await createProject(name, "", "");
      setProjects((prev) => [created, ...prev]);
      setActiveId(created.id);
      setOpenCreate(false);
      setNewProjectName("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Не удалось создать проект");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <FolderOpenIcon className="h-5 w-5 text-[var(--app-primary)]" />
            <div className="text-sm font-semibold text-[var(--app-text)]">Проекты</div>
          </div>
          <Chip size="sm" variant="flat">{filtered.length}</Chip>
        </div>
        <Card className="app-card">
          <CardBody className="space-y-3">
            <div className="flex gap-2">
              <Input value={q} onValueChange={setQ} placeholder="Поиск проекта" size="sm" className="flex-1" />
              <Button className="btn-primary" onPress={() => setOpenCreate(true)}>
                Создать проект
              </Button>
            </div>
          </CardBody>
        </Card>
        <div className="gap-3 flex flex-col">
          {filtered.map((p) => {
              const warnings = p.mappingResult?.aiVerificationReport?.totalWarnings ?? 0;
              const status = warnings > 0 ? "warn" : p.mappingResult ? "ok" : "pending";
              const fav = !!favorites[p.id];
              return (
                <Card key={p.id} className="app-card">
                  <CardHeader className="justify-between">
                    <div>
                      <div className="text-sm font-semibold text-[var(--app-text)]">{p.name}</div>
                      <div className="mt-1 flex gap-1">
                        {p.bpmnXml && <Chip size="sm" variant="flat">BPMN</Chip>}
                        {p.openApiJson && <Chip size="sm" variant="flat">OpenAPI</Chip>}
                        {p.pumlContent && <Chip size="sm" variant="flat">PUML</Chip>}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <Button isIconOnly className="btn-ghost" onPress={() => {}}>
                        <BellIcon className="h-5 w-5" />
                      </Button>
                      <Button isIconOnly className="btn-ghost" onPress={() => setFavorites((f) => ({ ...f, [p.id]: !fav }))}>
                        <StarIcon className={`h-5 w-5 ${fav ? "text-yellow-400" : ""}`} />
                      </Button>
                    </div>
                  </CardHeader>
                  <CardBody className="grid grid-cols-2 gap-2 text-xs text-muted">
                    <div>статус анализа</div>
                    <div className="text-right">
                      {status === "ok" ? "✔ Пройден" : status === "warn" ? "⚠ Найдены ошибки" : "⏳ Ожидает"}
                    </div>
                    <div>сценариев</div>
                    <div className="text-right">0</div>
                    <div>шагов</div>
                    <div className="text-right">{p.mappingResult?.totalTasks ?? 0}</div>
                    <div>последний анализ</div>
                    <div className="text-right">—</div>
                  </CardBody>
                  <CardFooter className="flex gap-2 justify-between">
                    <Button className="btn-outline" startContent={<ArrowPathIcon className="h-4 w-4" />} onPress={() => handleRemap()}>
                      Ремаппинг
                    </Button>
                    <Button className="btn-primary" onPress={() => navigate(`/projects/${p.id}`)}>Открыть проект</Button>
                  </CardFooter>
                </Card>
              );
            })}
            {filtered.length === 0 && (
              <div className="text-sm text-muted">Ничего не найдено</div>
            )}
          </div>
        </div>
      {openCreate ? (
        <Modal isOpen={openCreate} onOpenChange={setOpenCreate}>
          <ModalContent>
            <ModalHeader>Создать проект</ModalHeader>
            <ModalBody>
              <Input 
                label="Название проекта" 
                placeholder="Введите имя" 
                size="sm" 
                value={newProjectName}
                onValueChange={setNewProjectName}
                onKeyDown={(e) => { if (e.key === "Enter") handleCreateProject(); }}
              />
              <div className="text-xs text-muted">Загрузка артефактов будет добавлена позже</div>
            </ModalBody>
            <ModalFooter>
              <Button className="btn-ghost" onPress={() => setOpenCreate(false)}>Отмена</Button>
              <Button className="btn-primary" onPress={handleCreateProject} isDisabled={!newProjectName.trim()} isLoading={loading}>Создать</Button>
            </ModalFooter>
          </ModalContent>
        </Modal>
      ) : null}
    </div>
  );
};

export default ProjectsPage;