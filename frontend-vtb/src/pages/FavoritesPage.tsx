import { useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import {
  Card,
  CardBody,
  CardHeader,
  Chip,
  Input,
} from "@heroui/react";
import {
  MagnifyingGlassIcon,
  StarIcon,
  CheckCircleIcon,
  ExclamationTriangleIcon,
  ClockIcon,
} from "@heroicons/react/24/outline";
import { useProjectStore } from "@/stores/project-store";
import { ProjectDto } from "@/lib/testflow-api";

function getProjectStatus(project: ProjectDto): "passed" | "errors" | "pending" {
  if (!project.mappingResult) return "pending";
  const hasErrors =
    (project.mappingResult.unmatchedTasks?.length ?? 0) > 0 ||
    (project.mappingResult.overallConfidence ?? 0) < 0.5;
  return hasErrors ? "errors" : "passed";
}

const FavoritesPage = () => {
  const navigate = useNavigate();
  const { projects, favorites, loadProjects, setFilters, filters } = useProjectStore();

  useEffect(() => {
    loadProjects();
  }, [loadProjects]);

  const favoriteProjects = useMemo(() => {
    return projects.filter((p) => favorites.has(p.id));
  }, [projects, favorites]);

  const handleOpenProject = (projectId: string) => {
    navigate(`/projects/${projectId}`);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <StarIcon className="w-6 h-6 text-warning" />
        <h1 className="text-2xl font-bold text-[var(--app-text)]">Избранное</h1>
      </div>

      <div className="flex items-center gap-3">
        <Input
          placeholder="Поиск проекта"
          size="sm"
          startContent={<MagnifyingGlassIcon className="w-4 h-4" />}
          value={filters.searchQuery}
          onChange={(e) => setFilters({ searchQuery: e.target.value })}
          className="flex-1 max-w-md"
        />
      </div>

      {favoriteProjects.length === 0 ? (
        <Card className="app-card">
          <CardBody className="text-center py-12">
            <StarIcon className="w-12 h-12 text-muted mx-auto mb-4" />
            <div className="text-muted">
              Нет избранных проектов. Добавьте проекты в избранное на странице
              проектов.
            </div>
          </CardBody>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {favoriteProjects.map((project) => {
            const status = getProjectStatus(project);
            const artifactTypes: string[] = [];
            if (project.bpmnXml) artifactTypes.push("BPMN");
            if (project.openApiJson) artifactTypes.push("OpenAPI");
            if (project.pumlContent) artifactTypes.push("PUML");

            return (
              <Card
                key={project.id}
                className="app-card hover:border-[var(--app-primary)]/50 transition-colors"
              >
                <CardHeader className="flex-col items-start gap-2">
                  <div className="flex items-start justify-between w-full">
                    <div className="flex-1">
                      <h3 className="text-lg font-semibold text-[var(--app-text)]">
                        {project.name}
                      </h3>
                      <div className="flex flex-wrap gap-1 mt-2">
                        {artifactTypes.map((type) => (
                          <Chip key={type} size="sm" variant="flat">
                            {type}
                          </Chip>
                        ))}
                      </div>
                    </div>
                    <StarIcon className="w-5 h-5 fill-warning text-warning" />
                  </div>

                  <div className="flex items-center gap-2 w-full">
                    {status === "passed" && (
                      <Chip
                        color="success"
                        size="sm"
                        startContent={<CheckCircleIcon className="w-3 h-3" />}
                      >
                        Пройден
                      </Chip>
                    )}
                    {status === "errors" && (
                      <Chip
                        color="danger"
                        size="sm"
                        startContent={<ExclamationTriangleIcon className="w-3 h-3" />}
                      >
                        Найдены ошибки
                      </Chip>
                    )}
                    {status === "pending" && (
                      <Chip
                        color="warning"
                        size="sm"
                        startContent={<ClockIcon className="w-3 h-3" />}
                      >
                        Ожидает
                      </Chip>
                    )}
                  </div>
                </CardHeader>

                <CardBody className="pt-0">
                  <div className="space-y-2 text-sm text-muted">
                    {project.mappingResult && (
                      <>
                        <div>
                          Сценариев: {project.mappingResult.matchedTasks ?? 0}
                        </div>
                        <div>
                          Шагов:{" "}
                          {Object.keys(project.mappingResult.taskMappings ?? {}).length}
                        </div>
                      </>
                    )}
                  </div>

                  <button
                    className="mt-4 w-full rounded-lg bg-[var(--app-primary)] text-white py-2 text-sm font-medium hover:opacity-90 transition-opacity"
                    onClick={() => handleOpenProject(project.id)}
                  >
                    Открыть проект
                  </button>
                </CardBody>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default FavoritesPage;

