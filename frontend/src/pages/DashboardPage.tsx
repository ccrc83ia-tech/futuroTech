import AccessTimeIcon from "@mui/icons-material/AccessTime";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import DescriptionOutlinedIcon from "@mui/icons-material/DescriptionOutlined";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { documentsApi, templatesApi } from "../api/endpoints";
import type { GeneratedDoc } from "../api/types";
import { PERMISSIONS } from "../api/types";
import { useAuthStore } from "../store/auth";

// ─── Helpers de plazo ─────────────────────────────────────────────────────────

function deadlineDaysLeft(deadline: string | null): number | null {
  if (!deadline) return null;
  const diff = new Date(deadline).getTime() - Date.now();
  return Math.ceil(diff / (1000 * 60 * 60 * 24));
}

function DeadlineChip({ deadline }: { deadline: string | null }) {
  const days = deadlineDaysLeft(deadline);
  if (days === null) return <Typography variant="body2" color="text.disabled">—</Typography>;
  if (days < 0) return <Chip label="Vencido" color="error" size="small" icon={<WarningAmberIcon />} />;
  if (days <= 3) return <Chip label={`${days}d`} color="error" size="small" icon={<WarningAmberIcon />} />;
  if (days <= 7) return <Chip label={`${days}d`} color="warning" size="small" icon={<AccessTimeIcon />} />;
  return <Chip label={`${days}d`} color="success" size="small" icon={<AccessTimeIcon />} />;
}

// ─── Tarjeta de métrica ───────────────────────────────────────────────────────

function MetricCard({
  label, value, sublabel, color, icon, onClick,
}: {
  label: string;
  value: number | string;
  sublabel?: string;
  color: "primary" | "warning" | "error" | "success";
  icon: React.ReactNode;
  onClick?: () => void;
}) {
  const colorMap = {
    primary: "#1976d2",
    warning: "#ed6c02",
    error:   "#d32f2f",
    success: "#2e7d32",
  };
  return (
    <Card
      variant="outlined"
      onClick={onClick}
      sx={{
        flex: 1, minWidth: 160, cursor: onClick ? "pointer" : "default",
        borderLeft: `4px solid ${colorMap[color]}`,
        "&:hover": onClick ? { boxShadow: 3 } : {},
        transition: "box-shadow 0.2s",
      }}
    >
      <CardContent sx={{ pb: "12px !important" }}>
        <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <Box>
            <Typography variant="h3" fontWeight="bold" color={`${color}.main`}>
              {value}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              {label}
            </Typography>
            {sublabel && (
              <Typography variant="caption" color="text.disabled">{sublabel}</Typography>
            )}
          </Box>
          <Box sx={{ color: `${color}.main`, opacity: 0.7, mt: 0.5 }}>
            {icon}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
}

// ─── Página principal ─────────────────────────────────────────────────────────

export default function DashboardPage() {
  const navigate = useNavigate();
  const { fullName, hasPermission } = useAuthStore();
  const canReview  = hasPermission(PERMISSIONS.MANAGE_USERS) || hasPermission(PERMISSIONS.MANAGE_TEMPLATES);
  const canGenerate = hasPermission(PERMISSIONS.GENERATE_DOCUMENT);

  const statsQuery    = useQuery({ queryKey: ["stats"],     queryFn: documentsApi.stats,     enabled: canReview });
  const allDocsQuery  = useQuery({ queryKey: ["allDocs"],   queryFn: documentsApi.all,        enabled: canReview });
  const myDocsQuery   = useQuery({ queryKey: ["myDocs"],    queryFn: documentsApi.mine,       enabled: canGenerate });
  const templatesQuery = useQuery({ queryKey: ["templates"], queryFn: templatesApi.list });

  const templateName = useMemo(() => {
    const map = new Map<string, string>();
    (templatesQuery.data ?? []).forEach((t) => map.set(t.templateId, t.name));
    return (id: string) => map.get(id) ?? id;
  }, [templatesQuery.data]);

  // Documentos próximos a vencer o vencidos (ordenados por urgencia)
  const urgentDocs: GeneratedDoc[] = useMemo(() => {
    const docs = canReview ? (allDocsQuery.data ?? []) : (myDocsQuery.data ?? []);
    return docs
      .filter((d) => d.deadline && d.status !== "APPROVED" && d.status !== "VOID")
      .sort((a, b) => new Date(a.deadline!).getTime() - new Date(b.deadline!).getTime())
      .slice(0, 10);
  }, [allDocsQuery.data, myDocsQuery.data, canReview]);

  // Borradores pendientes de revisión
  const pendingDocs: GeneratedDoc[] = useMemo(() => {
    const docs = canReview ? (allDocsQuery.data ?? []) : (myDocsQuery.data ?? []);
    return docs.filter((d) => d.status === "DRAFT").slice(0, 5);
  }, [allDocsQuery.data, myDocsQuery.data, canReview]);

  const isLoading = canReview ? (statsQuery.isLoading || allDocsQuery.isLoading) : myDocsQuery.isLoading;

  const greeting = useMemo(() => {
    const hour = new Date().getHours();
    if (hour < 12) return "Buenos días";
    if (hour < 18) return "Buenas tardes";
    return "Buenas noches";
  }, []);

  return (
    <Box>
      {/* Saludo */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" fontWeight="medium">
          {greeting}, {fullName?.split(" ")[0] ?? ""}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Aquí tiene el resumen del despacho.
        </Typography>
      </Box>

      {isLoading && <LinearProgress sx={{ mb: 2 }} />}

      {/* Métricas (solo admin/abogado) */}
      {canReview && statsQuery.data && (
        <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mb: 4 }}>
          <MetricCard
            label="Documentos este mes"
            value={statsQuery.data.totalMonth}
            color="primary"
            icon={<DescriptionOutlinedIcon sx={{ fontSize: 36 }} />}
            onClick={() => navigate("/todos")}
          />
          <MetricCard
            label="Pendientes de revisión"
            value={statsQuery.data.pendingReview}
            sublabel="Borradores por aprobar"
            color={statsQuery.data.pendingReview > 0 ? "warning" : "success"}
            icon={<CheckCircleOutlineIcon sx={{ fontSize: 36 }} />}
            onClick={() => navigate("/todos")}
          />
          <MetricCard
            label="Vencen en 7 días"
            value={statsQuery.data.upcomingDeadlines}
            sublabel="Plazos próximos a cumplirse"
            color={statsQuery.data.upcomingDeadlines > 0 ? "warning" : "success"}
            icon={<AccessTimeIcon sx={{ fontSize: 36 }} />}
          />
          <MetricCard
            label="Vencidos sin respuesta"
            value={statsQuery.data.overdue}
            sublabel="Requieren atención urgente"
            color={statsQuery.data.overdue > 0 ? "error" : "success"}
            icon={<WarningAmberIcon sx={{ fontSize: 36 }} />}
          />
        </Box>
      )}

      {/* Acceso rápido */}
      {canGenerate && (
        <Box sx={{ display: "flex", gap: 1.5, mb: 4, flexWrap: "wrap" }}>
          <Button variant="contained" size="large" onClick={() => navigate("/generar")}>
            + Nuevo documento
          </Button>
          <Button variant="outlined" onClick={() => navigate("/documentos")}>
            Mis documentos
          </Button>
          {canReview && (
            <Button variant="outlined" color="warning" onClick={() => navigate("/todos")}>
              Revisar borradores {statsQuery.data?.pendingReview ? `(${statsQuery.data.pendingReview})` : ""}
            </Button>
          )}
        </Box>
      )}

      {/* Documentos por vencer */}
      {urgentDocs.length > 0 && (
        <Box sx={{ mb: 4 }}>
          <Typography variant="h6" gutterBottom>Control de plazos</Typography>
          <Alert severity="info" sx={{ mb: 1.5 }}>
            Un derecho de petición tiene <strong>15 días hábiles</strong> de plazo legal y una tutela <strong>10 días hábiles</strong>.
            El plazo se cuenta desde la fecha de generación del documento.
          </Alert>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Tipo</TableCell>
                <TableCell>Cliente</TableCell>
                <TableCell>Dirigido a</TableCell>
                <TableCell>Radicado</TableCell>
                <TableCell>Estado</TableCell>
                <TableCell>Vence</TableCell>
                <TableCell>Generado por</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {urgentDocs.map((d) => (
                <TableRow
                  key={d.transactionId}
                  hover
                  sx={{ cursor: "pointer" }}
                  onClick={() => navigate(canReview ? "/todos" : "/documentos")}
                >
                  <TableCell sx={{ maxWidth: 150 }}>
                    <Tooltip title={templateName(d.templateId)}>
                      <Typography variant="body2" noWrap>{templateName(d.templateId)}</Typography>
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{d.clientName ?? "—"}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{d.entityName ?? "—"}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2" color={d.internalDocket ? "text.primary" : "text.disabled"}>
                      {d.internalDocket ?? "Sin radicar"}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={d.status === "DRAFT" ? "Borrador" : d.status === "REVIEWED" ? "Revisado" : d.status}
                      color={d.status === "DRAFT" ? "warning" : "info"}
                      size="small"
                    />
                  </TableCell>
                  <TableCell><DeadlineChip deadline={d.deadline} /></TableCell>
                  <TableCell>
                    <Typography variant="body2">{d.authorUserId ?? "—"}</Typography>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}

      {/* Borradores pendientes (solo si hay) */}
      {canReview && pendingDocs.length > 0 && (
        <Box>
          <Divider sx={{ mb: 2 }} />
          <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1.5 }}>
            <Typography variant="h6">
              Borradores pendientes de aprobación
            </Typography>
            <Button size="small" onClick={() => navigate("/todos")}>Ver todos</Button>
          </Box>
          <Alert severity="warning" sx={{ mb: 1.5 }}>
            Estos documentos fueron generados por el equipo y esperan su revisión antes de ser enviados.
          </Alert>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Tipo</TableCell>
                <TableCell>Cliente</TableCell>
                <TableCell>Autor</TableCell>
                <TableCell>Fecha</TableCell>
                <TableCell>Vence</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {pendingDocs.map((d) => (
                <TableRow key={d.transactionId} hover sx={{ cursor: "pointer" }} onClick={() => navigate("/todos")}>
                  <TableCell>
                    <Typography variant="body2">{templateName(d.templateId)}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{d.clientName ?? "—"}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{d.authorUserId ?? "—"}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">
                      {new Date(d.createdAt).toLocaleDateString("es-CO")}
                    </Typography>
                  </TableCell>
                  <TableCell><DeadlineChip deadline={d.deadline} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      )}

      {/* Estado vacío */}
      {!isLoading && urgentDocs.length === 0 && pendingDocs.length === 0 && (
        <Box sx={{ textAlign: "center", py: 6, color: "text.secondary" }}>
          <CheckCircleOutlineIcon sx={{ fontSize: 64, mb: 1, color: "success.light" }} />
          <Typography variant="h6">Todo al día</Typography>
          <Typography variant="body2">No hay documentos urgentes ni borradores pendientes.</Typography>
          {canGenerate && (
            <Button variant="contained" sx={{ mt: 2 }} onClick={() => navigate("/generar")}>
              Generar nuevo documento
            </Button>
          )}
        </Box>
      )}
    </Box>
  );
}
