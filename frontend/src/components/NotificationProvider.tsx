import { Alert, Snackbar } from "@mui/material";
import { createContext, ReactNode, useCallback, useContext, useMemo, useRef, useState } from "react";

type Severity = "success" | "error" | "info" | "warning";

interface Notification {
  id: number;
  message: string;
  severity: Severity;
}

interface NotifyApi {
  notify: (message: string, severity?: Severity) => void;
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
  warning: (message: string) => void;
}

const NotificationContext = createContext<NotifyApi | null>(null);

/**
 * Proveedor global de notificaciones tipo "toast". Muestra un snackbar a la vez,
 * encolando los siguientes. Úselo vía el hook useNotify().
 */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const queueRef = useRef<Notification[]>([]);
  const [current, setCurrent] = useState<Notification | null>(null);
  const [open, setOpen] = useState(false);

  const processQueue = useCallback(() => {
    const next = queueRef.current.shift();
    if (next) {
      setCurrent(next);
      setOpen(true);
    }
  }, []);

  const notify = useCallback(
    (message: string, severity: Severity = "info") => {
      const item: Notification = { id: Date.now() + Math.random(), message, severity };
      queueRef.current.push(item);
      // Si no hay un snackbar visible, procesamos de inmediato.
      setOpen((isOpen) => {
        if (!isOpen) {
          const next = queueRef.current.shift();
          if (next) {
            setCurrent(next);
            return true;
          }
        }
        return isOpen;
      });
    },
    [],
  );

  const api = useMemo<NotifyApi>(
    () => ({
      notify,
      success: (m: string) => notify(m, "success"),
      error: (m: string) => notify(m, "error"),
      info: (m: string) => notify(m, "info"),
      warning: (m: string) => notify(m, "warning"),
    }),
    [notify],
  );

  function handleClose(_: unknown, reason?: string) {
    if (reason === "clickaway") {
      return;
    }
    setOpen(false);
  }

  function handleExited() {
    setCurrent(null);
    processQueue();
  }

  return (
    <NotificationContext.Provider value={api}>
      {children}
      <Snackbar
        key={current?.id}
        open={open}
        autoHideDuration={current?.severity === "error" ? 6000 : 4000}
        onClose={handleClose}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
        slotProps={{ transition: { onExited: handleExited } }}
      >
        <Alert
          onClose={handleClose}
          severity={current?.severity ?? "info"}
          variant="filled"
          sx={{ width: "100%" }}
        >
          {current?.message}
        </Alert>
      </Snackbar>
    </NotificationContext.Provider>
  );
}

/** Acceso al API de notificaciones. Debe usarse dentro de <NotificationProvider>. */
export function useNotify(): NotifyApi {
  const ctx = useContext(NotificationContext);
  if (!ctx) {
    throw new Error("useNotify debe usarse dentro de NotificationProvider");
  }
  return ctx;
}
