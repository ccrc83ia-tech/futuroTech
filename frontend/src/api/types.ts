// Tipos alineados con los DTOs del backend.

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: string;
  username: string;
  fullName: string;
  role: string;
  permissions: string[];
}

export interface UserResponse {
  userId: string;
  username: string;
  fullName: string;
  email: string;
  role: string;
  active: boolean;
  permissions: string[];
}

export interface Template {
  templateId: string;
  name: string;
  description: string;
  fields: string[];
  systemPrompt: string;
  legalRules: string[];
  ragStrict: boolean;
  formatDocName: string | null;
  hasFormatDoc: boolean;
}

export interface RecipientEntity {
  entityId: string;
  name: string;
  nit: string;
  address: string;
  city: string;
  phone: string;
  email: string;
  legalRep: string;
  entityType: string;
  notes: string;
}

export interface Branding {
  logoUrl: string | null;
  headerText: string | null;
  footerText: string | null;
  primaryColor: string;
  nit: string | null;
  address: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
}

export interface Company {
  tenantId: string;
  name: string;
  systemPrompt: string;
  legalRules: string[];
  branding: Branding;
  active: boolean;
}

export type DocumentStatus = "DRAFT" | "REVIEWED" | "APPROVED" | "VOID";

export interface GeneratedDoc {
  transactionId: string;
  templateId: string;
  status: DocumentStatus;
  fileName: string;
  downloadUrl: string;
  createdAt: string;
  // Campos enriquecidos para trazabilidad
  clientName: string | null;
  entityId: string | null;
  entityName: string | null;
  authorUserId: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
}

export interface GenerateRequest {
  templateId: string;
  entityId?: string;
  caseFields: Record<string, string>;
  selectedRules?: string[];
  ragStrict?: boolean;
}

// Permisos (deben coincidir con el enum del backend)
export const PERMISSIONS = {
  GENERATE_DOCUMENT: "generate_document",
  VIEW_TEMPLATES: "view_templates",
  MANAGE_TEMPLATES: "manage_templates",
  VIEW_ENTITIES: "view_entities",
  MANAGE_ENTITIES: "manage_entities",
  VIEW_COMPANY: "view_company",
  MANAGE_COMPANY: "manage_company",
  MANAGE_USERS: "manage_users",
} as const;
