import {useEffect, type ReactNode} from 'react';
import {Outlet} from 'react-router-dom';

export function RouteTitle({title, children}: {title: string; children?: ReactNode}) {
  useEffect(() => { document.title = title; }, [title]);
  return children ?? <Outlet/>;
}
