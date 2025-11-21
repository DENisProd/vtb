import { create } from "zustand";
import { devtools } from "zustand/middleware";
import { ProjectDto, listProjects, getProject, createProject, remapProject } from "@/lib/testflow-api";

export type ProjectStatus = "passed" | "errors" | "pending";
export type ArtifactTypeFilter = "bpmn" | "puml" | "openapi" | "all";

export interface ProjectFilters {
  searchQuery: string;
  dateUpdated: "all" | "today" | "week" | "month";
  author: string;
  artifactType: ArtifactTypeFilter;
  status: ProjectStatus | "all";
}

interface ProjectStore {
  projects: ProjectDto[];
  selectedProjectId: string | null;
  favorites: Set<string>;
  filters: ProjectFilters;
  loading: boolean;
  error: string | null;

  // Actions
  loadProjects: () => Promise<void>;
  selectProject: (id: string | null) => void;
  getSelectedProject: () => ProjectDto | null;
  toggleFavorite: (id: string) => void;
  setFilters: (filters: Partial<ProjectFilters>) => void;
  createNewProject: (params: {
    name: string;
    bpmnXml: string;
    openApiJson: string;
    pumlContent?: string;
  }) => Promise<ProjectDto>;
  refreshProject: (id: string) => Promise<void>;
  runAnalysis: (id: string) => Promise<void>;
}

const defaultFilters: ProjectFilters = {
  searchQuery: "",
  dateUpdated: "all",
  author: "",
  artifactType: "all",
  status: "all",
};

export const useProjectStore = create<ProjectStore>()(
  devtools(
    (set, get) => ({
      projects: [],
      selectedProjectId: null,
      favorites: new Set<string>(),
      filters: defaultFilters,
      loading: false,
      error: null,

      loadProjects: async () => {
        set({ loading: true, error: null });
        try {
          const projects = await listProjects();
          set({ projects, loading: false });
        } catch (error) {
          set({
            error: error instanceof Error ? error.message : "Не удалось загрузить проекты",
            loading: false,
          });
        }
      },

      selectProject: (id) => {
        set({ selectedProjectId: id });
      },

      getSelectedProject: () => {
        const { projects, selectedProjectId } = get();
        return projects.find((p) => p.id === selectedProjectId) ?? null;
      },

      toggleFavorite: (id) => {
        set((state) => {
          const newFavorites = new Set(state.favorites);
          if (newFavorites.has(id)) {
            newFavorites.delete(id);
          } else {
            newFavorites.add(id);
          }
          // Сохраняем в localStorage
          if (typeof window !== "undefined") {
            localStorage.setItem("projectFavorites", JSON.stringify(Array.from(newFavorites)));
          }
          return { favorites: newFavorites };
        });
      },

      setFilters: (newFilters) => {
        set((state) => ({
          filters: { ...state.filters, ...newFilters },
        }));
      },

      createNewProject: async (params) => {
        set({ loading: true, error: null });
        try {
          const project = await createProject(
            params.name,
            params.bpmnXml,
            params.openApiJson,
            params.pumlContent,
          );
          set((state) => ({
            projects: [project, ...state.projects],
            selectedProjectId: project.id,
            loading: false,
          }));
          return project;
        } catch (error) {
          set({
            error: error instanceof Error ? error.message : "Не удалось создать проект",
            loading: false,
          });
          throw error;
        }
      },

      refreshProject: async (id) => {
        try {
          const project = await getProject(id);
          set((state) => ({
            projects: state.projects.map((p) => (p.id === id ? project : p)),
          }));
        } catch (error) {
          set({
            error: error instanceof Error ? error.message : "Не удалось обновить проект",
          });
        }
      },

      runAnalysis: async (id) => {
        set({ loading: true, error: null });
        try {
          const updated = await remapProject(id);
          set((state) => ({
            projects: state.projects.map((p) => (p.id === id ? updated : p)),
            loading: false,
          }));
        } catch (error) {
          set({
            error: error instanceof Error ? error.message : "Не удалось выполнить анализ",
            loading: false,
          });
        }
      },
    }),
    { name: "project-store" },
  ),
);

// Загружаем избранное из localStorage при инициализации
if (typeof window !== "undefined") {
  const saved = localStorage.getItem("projectFavorites");
  if (saved) {
    try {
      const favorites = JSON.parse(saved) as string[];
      useProjectStore.setState({ favorites: new Set(favorites) });
    } catch {
      // ignore
    }
  }
}

