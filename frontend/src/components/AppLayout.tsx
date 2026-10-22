import AddCircleIcon from "@mui/icons-material/AddCircle";
import ArticleIcon from "@mui/icons-material/Article";
import BusinessIcon from "@mui/icons-material/Business";
import ContactsIcon from "@mui/icons-material/Contacts";
import DescriptionIcon from "@mui/icons-material/Description";
import FolderOpenIcon from "@mui/icons-material/FolderOpen";
import GroupIcon from "@mui/icons-material/Group";
import LogoutIcon from "@mui/icons-material/Logout";
import MenuIcon from "@mui/icons-material/Menu";
import {
  AppBar,
  Avatar,
  Box,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { ReactNode, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { PERMISSIONS } from "../api/types";
import { useAuthStore } from "../store/auth";

const DRAWER_WIDTH = 240;

interface NavItem {
  label: string;
  path: string;
  icon: ReactNode;
  permission?: string;
}

const NAV: NavItem[] = [
  { label: "Generar documento",    path: "/generar",    icon: <AddCircleIcon />,   permission: PERMISSIONS.GENERATE_DOCUMENT },
  { label: "Mis documentos",       path: "/documentos", icon: <DescriptionIcon />, permission: PERMISSIONS.GENERATE_DOCUMENT },
  { label: "Todos los documentos", path: "/todos",      icon: <FolderOpenIcon />,  permission: PERMISSIONS.MANAGE_USERS },
  { label: "Plantillas",           path: "/plantillas", icon: <ArticleIcon />,     permission: PERMISSIONS.VIEW_TEMPLATES },
  { label: "Entidades",            path: "/entidades",  icon: <ContactsIcon />,    permission: PERMISSIONS.VIEW_ENTITIES },
  { label: "Empresa",              path: "/empresa",    icon: <BusinessIcon />,    permission: PERMISSIONS.VIEW_COMPANY },
  { label: "Usuarios",             path: "/usuarios",   icon: <GroupIcon />,       permission: PERMISSIONS.MANAGE_USERS },
];

function initials(name?: string | null): string {
  if (!name) {
    return "?";
  }
  const parts = name.trim().split(/\s+/);
  return (parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "");
}

export default function AppLayout({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const [mobileOpen, setMobileOpen] = useState(false);
  const { fullName, role, logout, hasPermission } = useAuthStore();

  const visibleNav = NAV.filter((item) => !item.permission || hasPermission(item.permission));
  const currentLabel = NAV.find((i) => i.path === location.pathname)?.label;

  function go(path: string) {
    navigate(path);
    setMobileOpen(false);
  }

  const drawerContent = (
    <>
      <Toolbar />
      <Box sx={{ overflow: "auto", display: "flex", flexDirection: "column", height: "100%" }}>
        <List component="nav" aria-label="Navegación principal" sx={{ flexGrow: 1 }}>
          {visibleNav.map((item) => (
            <ListItemButton
              key={item.path}
              selected={location.pathname === item.path}
              onClick={() => go(item.path)}
              sx={{
                mx: 1,
                my: 0.25,
                borderRadius: 1.5,
                "&.Mui-selected": {
                  bgcolor: "primary.main",
                  color: "primary.contrastText",
                  "& .MuiListItemIcon-root": { color: "primary.contrastText" },
                  "&:hover": { bgcolor: "primary.dark" },
                },
              }}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ))}
        </List>
        <Divider />
        <List>
          <ListItemButton onClick={() => { logout(); navigate("/login"); }} sx={{ mx: 1, my: 0.5, borderRadius: 1.5 }}>
            <ListItemIcon sx={{ minWidth: 40 }}><LogoutIcon /></ListItemIcon>
            <ListItemText primary="Cerrar sesión" />
          </ListItemButton>
        </List>
      </Box>
    </>
  );

  return (
    <Box sx={{ display: "flex" }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          {isMobile && (
            <IconButton
              color="inherit"
              aria-label="Abrir menú de navegación"
              edge="start"
              onClick={() => setMobileOpen((v) => !v)}
              sx={{ mr: 1 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
            {isMobile ? (currentLabel ?? "FuturoTech AI") : "FuturoTech AI — Motor Jurídico"}
          </Typography>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
            {!isMobile && (
              <Box sx={{ textAlign: "right", lineHeight: 1.2 }}>
                <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
                  {fullName}
                </Typography>
                <Typography variant="caption" sx={{ opacity: 0.85, textTransform: "capitalize" }}>
                  {role}
                </Typography>
              </Box>
            )}
            <Tooltip title={`${fullName ?? ""} · ${role ?? ""}`}>
              <Avatar sx={{ width: 36, height: 36, bgcolor: "primary.dark", fontSize: 14 }}>
                {initials(fullName).toUpperCase()}
              </Avatar>
            </Tooltip>
          </Box>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        {/* Drawer temporal para móvil */}
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: "block", md: "none" },
            [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: "border-box" },
          }}
        >
          {drawerContent}
        </Drawer>
        {/* Drawer permanente para escritorio */}
        <Drawer
          variant="permanent"
          open
          sx={{
            display: { xs: "none", md: "block" },
            [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: "border-box" },
          }}
        >
          {drawerContent}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 2, sm: 3 },
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          minHeight: "100vh",
        }}
      >
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
}
