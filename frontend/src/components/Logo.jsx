import logoUrl from "../assets/warship-icon.svg";

export function Logo() {
  return (
    <div className="logo">
      <img src={logoUrl} alt="Salvo warship icon" />
    </div>
  );
}
