import ArticleIcon from "@mui/icons-material/Article";
import BusinessIcon from "@mui/icons-material/Business";
import DescriptionIcon from "@mui/icons-material/Description";
import GroupIcon from "@mui/icons-material/Group";
import LogoutIcon from "@mui/icons-material/Logout";
import AddCircleIcon from "@mui/icons-material/AddCircle";
import ContactsIcon from "@mui/icons-material/Contacts";
import {
  AppBar,
  Box,
  Drawer,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
} from "@mui/material";
import { ReactNode } from "react";
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
  { label: "Generar documento", path: "/generar", icon: <AddCircleIcon />, permission: PERMISSIONS.GENERATE_DOCUMENT },
  { label: "Mis documentos", path: "/documentos", icon: <DescriptionIcon />, permission: PERMISSIONS.GENERATE_DOCUMENT },
  { label: "Plantillas", path: "/plantillas", icon: <ArticleIcon />, permission: PERMISSIONS.VIEW_TEMPLATES },
  { label: "Entidades", path: "/entidades", icon: <ContactsIcon />, permission: PERMISSIONS.VIEW_ENTITIES },
  { label: "Empresa", path: "/empresa", icon: <BusinessIcon />, permission: PERMISSIONS.VIEW_COMPANY },
  { label: "Usuarios", path: "/usuarios", icon: <GroupIcon />, permission: PERMISSIONS.MANAGE_USERS },
];

export default function AppLayout({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { fullName, role, logout, hasPermission } = useAuthStore();

  const visibleNav = NAV.filter((item) => !item.permission || hasPermission(item.permission));

  return (
    <Box sx={{ display: "flex" }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>
            FuturoTech AI — Motor Jurídico
          </Typography>
          <Typography variant="body2" sx={{ mr: 2 }}>
            {fullName} · {role}
          </Typography>
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          [`& .MuiDrawer-paper`]: { width: DRAWER_WIDTH, boxSizing: "border-box" },
        }}
      >
        <Toolbar />
        <Box sx={{ overflow: "auto" }}>
          <List>
            {visibleNav.map((item) => (
              <ListItemButton
                key={item.path}
                selected={location.pathname === item.path}
                onClick={() => navigate(item.path)}
              >
                <ListItemIcon>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            ))}
            <ListItemButton onClick={() => { logout(); navigate("/login"); }}>
              <ListItemIcon><LogoutIcon /></ListItemIcon>
              <ListItemText primary="Cerrar sesión" />
            </ListItemButton>
          </List>
        </Box>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
}
