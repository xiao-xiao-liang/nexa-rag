import * as React from "react";
import { cn } from "../../lib/utils";

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {}

const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          "flex h-8 w-full rounded-[4px] border border-[#D0D3D6] bg-white px-2.5 py-1 text-xs text-[#1F2329] placeholder:text-[#8F959E] transition-all focus-visible:border-[#3370FF] focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-[#3370FF] disabled:cursor-not-allowed disabled:bg-[#F5F6F7] disabled:text-[#C9CDD4]",
          className
        )}
        ref={ref}
        {...props}
      />
    );
  }
);
Input.displayName = "Input";

export { Input };
