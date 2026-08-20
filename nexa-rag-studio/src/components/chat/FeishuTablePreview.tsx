import React from "react";

export interface TableRowData {
  id: number;
  name: string;
  dept: string;
  job: string;
  edu: string;
  origin: string;
  hireDate: string;
  school: string;
  phone: string;
  email: string;
  marriage: string;
  nation: string;
}

const DEFAULT_PREVIEW_DATA: TableRowData[] = [
  {
    id: 1,
    name: "宋方",
    dept: "财务部",
    job: "财务专员",
    edu: "大专",
    origin: "厦门",
    hireDate: "2021-02-11",
    school: "中山大学",
    phone: "13800138004",
    email: "songfang@gmail.com",
    marriage: "已婚",
    nation: "汉族",
  },
  {
    id: 2,
    name: "周甜",
    dept: "市场部",
    job: "高级市场专员",
    edu: "博士",
    origin: "北京",
    hireDate: "2021-03-06",
    school: "中山大学",
    phone: "13800138013",
    email: "zhoutian@gmail.com",
    marriage: "已婚",
    nation: "汉族",
  },
  {
    id: 3,
    name: "王冰",
    dept: "市场部",
    job: "海外市场专员",
    edu: "本科",
    origin: "长沙",
    hireDate: "2021-02-01",
    school: "清华大学",
    phone: "13800138001",
    email: "wangbing@gmail.com",
    marriage: "未婚",
    nation: "汉族",
  },
  {
    id: 4,
    name: "吴刚",
    dept: "市场部",
    job: "市场总监",
    edu: "博士",
    origin: "沈阳",
    hireDate: "2021-02-25",
    school: "复旦大学",
    phone: "13800138024",
    email: "wugang@gmail.com",
    marriage: "未婚",
    nation: "汉族",
  },
  {
    id: 5,
    name: "展纯",
    dept: "行政部",
    job: "行政主管",
    edu: "大专",
    origin: "吉林",
    hireDate: "2021-02-20",
    school: "武汉大学",
    phone: "13800138008",
    email: "zhanchun@gmail.com",
    marriage: "未婚",
    nation: "回族",
  },
];

export interface FeishuTablePreviewProps {
  data?: TableRowData[];
}

/** 1:1 飞书多维表格数据预览表格渲染器 */
export const FeishuTablePreview: React.FC<FeishuTablePreviewProps> = ({
  data = DEFAULT_PREVIEW_DATA,
}) => {
  return (
    <div className="border border-[#DEE0E3] rounded-[8px] overflow-hidden my-2 max-w-full bg-white">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-[13px] border-collapse">
          <thead>
            <tr className="bg-[#F5F6F7] text-[#646A73] border-b border-[#DEE0E3] font-medium whitespace-nowrap">
              <th className="py-2 px-3 text-center w-8 border-r border-[#DEE0E3]"></th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">员工姓名</th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">部门</th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">职位</th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">最高学历</th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">户籍所在地</th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">入职日期</th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">毕业院校</th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">联系电话</th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">电子邮箱</th>
              <th className="py-2 px-3 border-r border-[#DEE0E3]">婚姻状况</th>
              <th className="py-2 px-3">民族</th>
            </tr>
          </thead>
          <tbody className="text-[#1F2329] divide-y divide-[#DEE0E3]">
            {data.map((row) => (
              <tr key={row.id} className="hover:bg-[#F8F9FA] whitespace-nowrap">
                <td className="py-2 px-3 text-center bg-[#FAFAFA] text-[#8F959E] border-r border-[#DEE0E3]">
                  {row.id}
                </td>
                <td className="py-2 px-3 border-r border-[#DEE0E3]">{row.name}</td>
                <td className="py-2 px-3 border-r border-[#DEE0E3]">{row.dept}</td>
                <td className="py-2 px-3 border-r border-[#DEE0E3]">{row.job}</td>
                <td className="py-2 px-3 border-r border-[#DEE0E3]">{row.edu}</td>
                <td className="py-2 px-3 border-r border-[#DEE0E3]">{row.origin}</td>
                <td className="py-2 px-3 border-r border-[#DEE0E3]">{row.hireDate}</td>
                <td className="py-2 px-3 border-r border-[#DEE0E3]">{row.school}</td>
                <td className="py-2 px-3 border-r border-[#DEE0E3] font-mono">{row.phone}</td>
                <td className="py-2 px-3 border-r border-[#DEE0E3] text-[#1456F0] hover:underline cursor-pointer">
                  {row.email}
                </td>
                <td className="py-2 px-3 border-r border-[#DEE0E3]">{row.marriage}</td>
                <td className="py-2 px-3">{row.nation}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};
