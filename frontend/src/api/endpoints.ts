import api from "./client";
import type {
  AuthResponse,
  Company,
  GeneratedDoc,
  GenerateRequest,
  RecipientEntity,
  Template,
  UserResponse,
} from "./types";

// --- Auth ---
export const authApi = {
  login: (username: string, password: string) =>
    api.post<AuthResponse>("/auth/login", { username, password }).then((r) => r.data),
};

// --- Templates ---
export const templatesApi = {
  list: () => api.get<Template[]>("/templates").then((r) => r.data),
  get: (id: string) => api.get<Template>(`/templates/${id}`).then((r) => r.data),
  save: (t: Partial<Template> & { templateId: string }) =>
    api.post<Template>("/templates", t).then((r) => r.data),
  update: (id: string, t: Partial<Template>) =>
    api.put<Template>(`/templates/${id}`, { ...t, templateId: id }).then((r) => r.data),
  remove: (id: string) => api.delete(`/templates/${id}`),
  uploadFormatDoc: (id: string, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return api.post(`/templates/${id}/format-doc`, form);
  },
  deleteFormatDoc: (id: string) => api.delete(`/templates/${id}/format-doc`),
};

// --- Entities ---
export const entitiesApi = {
  list: () => api.get<RecipientEntity[]>("/entities").then((r) => r.data),
  save: (e: RecipientEntity) => api.post<RecipientEntity>("/entities", e).then((r) => r.data),
  update: (id: string, e: RecipientEntity) =>
    api.put<RecipientEntity>(`/entities/${id}`, e).then((r) => r.data),
  remove: (id: string) => api.delete(`/entities/${id}`),
};

// --- Users ---
export const usersApi = {
  list: () => api.get<UserResponse[]>("/users").then((r) => r.data),
  create: (u: Record<string, unknown>) => api.post<UserResponse>("/users", u).then((r) => r.data),
  update: (id: string, u: Record<string, unknown>) =>
    api.put<UserResponse>(`/users/${id}`, u).then((r) => r.data),
  remove: (id: string) => api.delete(`/users/${id}`),
};

// --- Company ---
export const companyApi = {
  get: () => api.get<Company>("/company").then((r) => r.data),
  update: (c: Partial<Company>) => api.put<Company>("/company", c).then((r) => r.data),
};

// --- Documents ---
export const documentsApi = {
  generate: (req: GenerateRequest) =>
    api.post<GeneratedDoc>("/documents/generate", req).then((r) => r.data),
  mine: () => api.get<GeneratedDoc[]>("/documents/mine").then((r) => r.data),
  review: (id: string, status: string) =>
    api.put<GeneratedDoc>(`/documents/${id}/review`, { status }).then((r) => r.data),
  downloadUrl: (id: string) => `/api/v1/documents/${id}/download`,
};
