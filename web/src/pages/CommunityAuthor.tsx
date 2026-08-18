export function CommunityAuthor({name, detail}: {name: string; detail: string}) {
  return <header className="community-author">
    <span className="avatar">{name.slice(0, 1).toUpperCase()}</span>
    <span><b>{name}</b><small>{detail}</small></span>
  </header>;
}
