import { useEffect } from "react";
import logoUrl from "../assets/warship-icon.svg";

export function Favicon() {
  useEffect(() => {
    const link = document.querySelector("link[rel='icon']") || document.createElement("link");
    link.rel = "icon";
    link.type = "image/svg+xml";
    link.href = logoUrl;
    document.head.appendChild(link);
  }, []);

  return null;
}
