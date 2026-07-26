import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

/** 合并 Tailwind 样式类。 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
