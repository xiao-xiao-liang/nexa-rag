import React, { createContext, useContext } from "react";

interface TableContextType {
  inHead: boolean;
  rowIndex: number;
}

const TableSectionContext = createContext<TableContextType>({
  inHead: false,
  rowIndex: 0,
});

/**
 * 1:1 飞书多维表格风格 GFM Table 容器
 */
export const FeishuTable: React.FC<React.HTMLAttributes<HTMLTableElement>> = ({
  children,
  ...props
}) => {
  return (
    <div className="base-chatbot-maker-md-comp-table base-chatbot-maker-message-container__specialRenderMessage">
      <div className="table-container custom-scrollbar">
        <table className="custom-table" {...props}>
          {children}
        </table>
      </div>
    </div>
  );
};

/**
 * 飞书表格表头 Thead
 */
export const FeishuThead: React.FC<React.HTMLAttributes<HTMLTableSectionElement>> = ({
  children,
  ...props
}) => {
  return (
    <thead {...props}>
      <TableSectionContext.Provider value={{ inHead: true, rowIndex: 0 }}>
        {children}
      </TableSectionContext.Provider>
    </thead>
  );
};

/**
 * 飞书表格表体 Tbody (自动计算行号并注入第一列 Sticky 序号列)
 */
export const FeishuTbody: React.FC<React.HTMLAttributes<HTMLTableSectionElement>> = ({
  children,
  ...props
}) => {
  let rIndex = 0;
  const enhancedChildren = React.Children.map(children, (child) => {
    if (React.isValidElement(child)) {
      const currentRow = ++rIndex;
      return (
        <TableSectionContext.Provider
          key={currentRow}
          value={{ inHead: false, rowIndex: currentRow }}
        >
          {child}
        </TableSectionContext.Provider>
      );
    }
    return child;
  });

  return <tbody {...props}>{enhancedChildren}</tbody>;
};

/**
 * 飞书表格行 Tr (自动在第 0 列补齐 <th></th> 或 <td>{rowIndex}</td>)
 */
export const FeishuTr: React.FC<React.HTMLAttributes<HTMLTableRowElement>> = ({
  children,
  ...props
}) => {
  const { inHead, rowIndex } = useContext(TableSectionContext);

  return (
    <tr {...props}>
      {inHead ? <th></th> : <td>{rowIndex}</td>}
      {children}
    </tr>
  );
};

export const FeishuTh: React.FC<React.ThHTMLAttributes<HTMLTableCellElement>> = ({
  children,
  ...props
}) => {
  return <th {...props}>{children}</th>;
};

export const FeishuTd: React.FC<React.TdHTMLAttributes<HTMLTableCellElement>> = ({
  children,
  ...props
}) => {
  return <td {...props}>{children}</td>;
};
