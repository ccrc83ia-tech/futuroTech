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

  // --- Corpus RAG (fuentes normativas autorizadas) por plantilla ---
  listRagDocuments: (id: string) =>
    api.get<string[]>(`/templates/${id}/documents`).then((r) => r.data),
  uploadRagDocument: (id: string, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return api
      .post<{ docName: string; chunks: number }>(`/templates/${id}/documents`, form)
      .then((r) => r.data);
  },
  deleteRagDocument: (id: string, docName: string) =>
    api.delete(`/templates/${id}/documents`, { params: { docName } }),
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
  /**
   * Genera un documento. Envía multipart/form-data: la parte "request" es el
   * JSON con los datos del caso y "attachments" son documentos del caso
   * (PDF/DOCX/TXT) opcionales que la IA usará como hechos.
   */
  generate: (req: GenerateRequest, attachments?: File[]) => {
    const form = new FormData();
    form.append(
      "request",
      new Blob([JSON.stringify(req)], { type: "application/json" }),
    );
    (attachments ?? []).forEach((file) => form.append("attachments", file));
    return api.post<GeneratedDoc>("/documents/generate", form).then((r) => r.data);
  },
  mine: () => api.get<GeneratedDoc[]>("/documents/mine").then((r) => r.data),
  all: () => api.get<GeneratedDoc[]>("/documents").then((r) => r.data),
  text: (id: string) =>
    api.get<{ text: string; status: string }>(`/documents/${id}/text`).then((r) => r.data),
  review: (id: string, status: string) =>
    api.put<GeneratedDoc>(`/documents/${id}/review`, { status }).then((r) => r.data),
  /**
   * Descarga el .docx a través de Axios para que el interceptor añada el token
   * JWT (una navegación directa por <a href> no lo adjuntaría y daría 403).
   * Recibe el archivo como blob y dispara la descarga en el navegador.
   */
  download: async (id: string, fileName?: string, format: "docx" | "pdf" = "docx") => {
    const resp = await api.get(`/documents/${id}/download`, {
      responseType: "blob",
      params: { format },
    });
    const disposition = resp.headers["content-disposition"] as string | undefined;
    const match = disposition?.match(/filename="?([^"]+)"?/);
    const name = fileName ?? match?.[1] ?? `documento-${id}.${format}`;

    const url = window.URL.createObjectURL(resp.data as Blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  },
};
