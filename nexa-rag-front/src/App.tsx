import { RouterProvider } from 'react-router-dom'
import { router } from '@/app/router'

/** 应用根组件，负责挂载全局路由。 */
export default function App() {
  return <RouterProvider router={router} />
}
