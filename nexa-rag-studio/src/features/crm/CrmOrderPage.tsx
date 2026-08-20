import React, { useState, useMemo } from "react";
import {
  FeishuDataTable,
  FeishuColumn,
  FeishuPill,
  FeishuTag,
  FeishuAvatar,
  FeishuCellMainSub,
  FeishuActionLink,
} from "../../components/ui/feishu-table";
import { FeishuSelect, FeishuSelectOption } from "../../components/ui/feishu-select";

interface OpportunityItem {
  id: string;
  customerName: string;
  description: string;
  stage: "意向沟通" | "赢单" | "商务谈判";
  contactName: string;
  amount?: number;
  priority?: "高" | "中" | "低";
  createdTime?: string;
}

interface OrderItem {
  orderId: string;
  orderNo: string;
  customerName: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  status: "COMPLETED" | "PAID" | "PENDING" | "CANCELLED";
  ownerName: string;
  orderDate: string;
}

const STAGE_OPTIONS: FeishuSelectOption[] = [
  { value: "ALL", label: "全部商机阶段" },
  { value: "意向沟通", label: "意向沟通", pillVariant: "blue" },
  { value: "商务谈判", label: "商务谈判", pillVariant: "orange" },
  { value: "赢单", label: "赢单", pillVariant: "green" },
];

export const CrmOrderPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState("opportunities");
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [priorityFilter, setPriorityFilter] = useState("全部");
  const [stageFilter, setStageFilter] = useState("ALL");

  // 1:1 飞书完整 45 条 Mock 数据 (包含日期列 createdTime)
  const fullOpportunities: OpportunityItem[] = [
    // --- 第 1 页 (1 ~ 20 条) ---
    { id: "1", customerName: "南昌恒通重型机械制造有限公司", description: "咨询机械加工设备的性能和售后服务", stage: "意向沟通", contactName: "何阳", priority: "高", createdTime: "2026-08-17" },
    { id: "2", customerName: "长春万佳连锁超市有限公司", description: "询问商品供货价格和优惠政策", stage: "意向沟通", contactName: "董明", priority: "中", createdTime: "2026-08-17" },
    { id: "3", customerName: "长春万佳连锁超市有限公司", description: "询问商品供货价格和优惠政策", stage: "意向沟通", contactName: "董明", priority: "低", createdTime: "2026-08-16" },
    { id: "4", customerName: "哈尔滨鑫源电子元件有限公司", description: "朋友推荐，想了解电子元件性能", stage: "意向沟通", contactName: "孙浩", priority: "高", createdTime: "2026-08-16" },
    { id: "5", customerName: "东莞盈科机械制造有限公司", description: "采购工业自动化设备", stage: "赢单", contactName: "高磊", amount: 130000, priority: "高", createdTime: "2026-08-15" },
    { id: "6", customerName: "福州慧诚医药科技有限公司", description: "采购电子元件", stage: "商务谈判", contactName: "张敏", amount: 120000, priority: "中", createdTime: "2026-08-15" },
    { id: "7", customerName: "大连华腾通信设备有限公司", description: "海外进出口渠道合作", stage: "意向沟通", contactName: "孙浩", amount: 170000, priority: "低", createdTime: "2026-08-14" },
    { id: "8", customerName: "长沙昊天物流运输有限公司", description: "汽车零件来图定制", stage: "赢单", contactName: "郭涛", amount: 150000, priority: "高", createdTime: "2026-08-14" },
    { id: "9", customerName: "郑州瑞丰食品加工有限公司", description: "采购环保纸业设备", stage: "商务谈判", contactName: "郑磊", amount: 100000, priority: "中", createdTime: "2026-08-13" },
    { id: "10", customerName: "重庆盛世房地产开发有限公司", description: "批量采购高档面料", stage: "意向沟通", contactName: "周伟", amount: 190000, priority: "低", createdTime: "2026-08-12" },
    { id: "11", customerName: "合肥创科自动化设备有限公司", description: "采购智慧物流系统", stage: "赢单", contactName: "李华", amount: 100000, priority: "高", createdTime: "2026-08-11" },
    { id: "12", customerName: "厦门嘉禾文化传媒有限公司", description: "采购家用电器配件", stage: "赢单", contactName: "张敏", amount: 100000, priority: "中", createdTime: "2026-08-10" },
    { id: "13", customerName: "武汉蓝海生物科技有限公司", description: "批量采购电子元件", stage: "意向沟通", contactName: "唐宁", amount: 160000, priority: "低", createdTime: "2026-08-09" },
    { id: "14", customerName: "西安启航教育咨询有限公司", description: "供应链长期供货合作", stage: "赢单", contactName: "何阳", amount: 100000, priority: "高", createdTime: "2026-08-08" },
    { id: "15", customerName: "苏州博信云计算有限公司", description: "采购数控加工设备", stage: "赢单", contactName: "林燕", amount: 150000, priority: "中", createdTime: "2026-08-07" },
    { id: "16", customerName: "青岛晟达环保设备有限公司", description: "批量采购照明灯具", stage: "意向沟通", contactName: "钱芳", amount: 130000, priority: "低", createdTime: "2026-08-06" },
    { id: "17", customerName: "成都龙腾软件开发有限公司", description: "国际贸易代理合作", stage: "意向沟通", contactName: "郑磊", amount: 160000, priority: "高", createdTime: "2026-08-05" },
    { id: "18", customerName: "杭州鼎盛电子商务有限公司", description: "汽车零部件加工合作", stage: "意向沟通", contactName: "吴磊", amount: 140000, priority: "中", createdTime: "2026-08-04" },
    { id: "19", customerName: "广州远光新能源有限公司", description: "采购造纸生产线设备", stage: "赢单", contactName: "周伟", amount: 180000, priority: "高", createdTime: "2026-08-03" },
    { id: "20", customerName: "南京中科智能装备股份有限公司", description: "采购服装面料", stage: "意向沟通", contactName: "李华", amount: 180000, priority: "低", createdTime: "2026-08-02" },

    // --- 第 2 页 (21 ~ 40 条) ---
    { id: "21", customerName: "北京辰星智能科技有限公司", description: "采购全景环视雷达系统与数据平台", stage: "赢单", contactName: "石新宇", amount: 160000, priority: "高", createdTime: "2026-08-01" },
    { id: "22", customerName: "天津东方实木家具制造有限公司", description: "盲点监测雷达设备采购咨询", stage: "商务谈判", contactName: "王预言", amount: 140000, priority: "中", createdTime: "2026-07-30" },
    { id: "23", customerName: "上海星辰进出口贸易有限公司", description: "进出口物流传感器及监控模块合作", stage: "意向沟通", contactName: "曾小北", amount: 180000, priority: "低", createdTime: "2026-07-29" },
    { id: "24", customerName: "深圳阳光微电子有限公司", description: "汽车雷达数据模块批量定制采购", stage: "赢单", contactName: "彭瑞", amount: 200000, priority: "高", createdTime: "2026-07-28" },
    { id: "25", customerName: "济南汇通机械设备有限公司", description: "采购数控车床及工业自动化附件", stage: "商务谈判", contactName: "何阳", amount: 110000, priority: "中", createdTime: "2026-07-27" },
    { id: "26", customerName: "沈阳彩虹时尚服饰有限公司", description: "自适应巡航雷达方案设计与咨询", stage: "意向沟通", contactName: "于小宁", amount: 135000, priority: "低", createdTime: "2026-07-26" },
    { id: "27", customerName: "无锡创联智能装备有限公司", description: "智能仓储分拣系统硬件升级合作", stage: "赢单", contactName: "高磊", amount: 220000, priority: "高", createdTime: "2026-07-25" },
    { id: "28", customerName: "宁波海蓝进出口有限公司", description: "传感器外贸批量订单供货协议", stage: "意向沟通", contactName: "张敏", amount: 125000, priority: "中", createdTime: "2026-07-24" },
    { id: "29", customerName: "石家庄恒信化工原料有限公司", description: "环保在线监测设备批量采购需求", stage: "商务谈判", contactName: "郑磊", amount: 105000, priority: "低", createdTime: "2026-07-23" },
    { id: "30", customerName: "昆明云天物流科技有限公司", description: "车载定位雷达设备批量采购意向", stage: "意向沟通", contactName: "郭涛", amount: 145000, priority: "高", createdTime: "2026-07-22" },
    { id: "31", customerName: "贵阳山河重工科技有限公司", description: "重型挖掘机电控系统定制采购", stage: "赢单", contactName: "周伟", amount: 310000, priority: "高", createdTime: "2026-07-21" },
    { id: "32", customerName: "兰州金源自动化仪表有限公司", description: "工业自动化控制模块采购合作", stage: "意向沟通", contactName: "李华", amount: 95000, priority: "中", createdTime: "2026-07-20" },
    { id: "33", customerName: "南宁东盟供应链管理有限公司", description: "跨境冷链温控传感器长期供货", stage: "商务谈判", contactName: "唐宁", amount: 130000, priority: "低", createdTime: "2026-07-19" },
    { id: "34", customerName: "太原晋通矿山机械制造有限公司", description: "矿用防爆雷达监测系统升级方案", stage: "赢单", contactName: "孙浩", amount: 270000, priority: "高", createdTime: "2026-07-18" },
    { id: "35", customerName: "呼和浩特北方新能源有限公司", description: "光伏电站智能监控终端采购", stage: "意向沟通", contactName: "林燕", amount: 155000, priority: "中", createdTime: "2026-07-17" },
    { id: "36", customerName: "银川塞上数码智能科技有限公司", description: "智慧农业喷灌控制传感器设备", stage: "商务谈判", contactName: "钱芳", amount: 88000, priority: "低", createdTime: "2026-07-16" },
    { id: "37", customerName: "西宁天路交通设施工程有限公司", description: "高速公路盲区雷达预警机定制", stage: "赢单", contactName: "董明", amount: 195000, priority: "高", createdTime: "2026-07-15" },
    { id: "38", customerName: "乌鲁木齐丝路通达国际物流有限公司", description: "铁路货运集装箱追踪标签批量采购", stage: "意向沟通", contactName: "吴磊", amount: 165000, priority: "中", createdTime: "2026-07-14" },
    { id: "39", customerName: "海口海瑞医药生物工程有限公司", description: "恒温无菌车间洁净度监控仪器采购", stage: "商务谈判", contactName: "何阳", amount: 115000, priority: "低", createdTime: "2026-07-13" },
    { id: "40", customerName: "烟台鲁东精密电子仪器有限公司", description: "高精度微波雷达测试仪设备采购", stage: "赢单", contactName: "高磊", amount: 240000, priority: "高", createdTime: "2026-07-12" },

    // --- 第 3 页 (41 ~ 45 条) ---
    { id: "41", customerName: "珠海横琴智汇信息技术有限公司", description: "智慧园区安防周界雷达系统方案", stage: "意向沟通", contactName: "张敏", amount: 175000, priority: "中", createdTime: "2026-07-11" },
    { id: "42", customerName: "佛山精工模具科技有限公司", description: "高精度数控冲床自动化改造项目", stage: "商务谈判", contactName: "郑磊", amount: 130000, priority: "低", createdTime: "2026-07-10" },
    { id: "43", customerName: "泉州闽南轻工机械有限公司", description: "制鞋流水线视觉与雷达定位系统", stage: "赢单", contactName: "周伟", amount: 145000, priority: "高", createdTime: "2026-07-09" },
    { id: "44", customerName: "洛阳中原轴承制造股份有限公司", description: "轴承探伤无损检测传感器设备采购", stage: "意向沟通", contactName: "李华", amount: 160000, priority: "中", createdTime: "2026-07-08" },
    { id: "45", customerName: "潍坊歌尔声学智能科技有限公司", description: "声学与雷达多模态感知模组联合研发", stage: "赢单", contactName: "孙浩", amount: 280000, priority: "高" },
  ];

  const sampleOrders: OrderItem[] = [
    { orderId: "1", orderNo: "SO-20260720-010", customerName: "天津东方实木家具制造有限公司", productName: "盲点监测雷达", quantity: 7, unitPrice: 2900, totalAmount: 20300, status: "COMPLETED", ownerName: "石新宇", orderDate: "2026/07/20" },
    { orderId: "2", orderNo: "SO-20260718-009", customerName: "长沙昊天物流运输有限公司", productName: "汽车雷达数据模块", quantity: 9, unitPrice: 1400, totalAmount: 12600, status: "PAID", ownerName: "王预言", orderDate: "2026/07/18" },
    { orderId: "3", orderNo: "SO-20260715-008", customerName: "沈阳彩虹时尚服饰有限公司", productName: "自适应巡航雷达", quantity: 4, unitPrice: 2000, totalAmount: 8000, status: "COMPLETED", ownerName: "于小宁", orderDate: "2026/07/15" },
    { orderId: "4", orderNo: "SO-20260712-007", customerName: "苏州银河家用电器制造有限公司", productName: "发射接收部件", quantity: 15, unitPrice: 1100, totalAmount: 16500, status: "CANCELLED", ownerName: "曾小北", orderDate: "2026/07/12" },
    { orderId: "5", orderNo: "SO-20260710-006", customerName: "成都红星建筑工程有限公司", productName: "辅助设备控制器", quantity: 20, unitPrice: 800, totalAmount: 16000, status: "PENDING", ownerName: "彭瑞", orderDate: "2026/07/10" },
  ];

  // 联动过滤商机数据
  const filteredOpportunities = useMemo(() => {
    return fullOpportunities.filter((item) => {
      const matchPriority = priorityFilter === "全部" || item.priority === priorityFilter;
      const matchStage = stageFilter === "ALL" || item.stage === stageFilter;
      return matchPriority && matchStage;
    });
  }, [fullOpportunities, priorityFilter, stageFilter]);

  // 截图 1:1 对应的商机列表 Columns
  const oppColumns: FeishuColumn<OpportunityItem>[] = [
    {
      key: "customerName",
      title: "客户名称",
      dataIndex: "customerName",
      dataType: "text",
      width: 260,
      render: (val) => <span className="font-normal text-[#1F2329] text-[14px]">{val}</span>,
    },
    {
      key: "description",
      title: "商机描述",
      dataIndex: "description",
      dataType: "text",
      width: 240,
      render: (val) => <span className="text-[#1F2329] text-[14px] font-normal">{val}</span>,
    },
    {
      key: "stage",
      title: "商机阶段",
      dataIndex: "stage",
      dataType: "select",
      width: 140,
      options: [
        { label: "意向沟通", value: "意向沟通", pillVariant: "blue" },
        { label: "商务谈判", value: "商务谈判", pillVariant: "orange" },
        { label: "赢单", value: "赢单", pillVariant: "green" },
      ],
      render: (val) => {
        if (val === "意向沟通") return <FeishuPill variant="blue" dotColor="#FF8800">意向沟通</FeishuPill>;
        if (val === "赢单") return <FeishuPill variant="green" dotColor="#00B42A">赢单</FeishuPill>;
        if (val === "商务谈判") return <FeishuPill variant="orange" dotColor="#FF8800">商务谈判</FeishuPill>;
        return <FeishuPill variant="gray">{val}</FeishuPill>;
      },
    },
    {
      key: "contactName",
      title: "客户联系人",
      dataIndex: "contactName",
      dataType: "user",
      width: 120,
      render: (val) => <FeishuTag>{val}</FeishuTag>,
    },
    {
      key: "amount",
      title: "商机金额",
      dataIndex: "amount",
      align: "right",
      dataType: "number",
      width: 120,
      render: (val) => (
        <span className="font-normal text-[#1F2329] text-[14px] tabular-nums">
          {val !== undefined ? val : "—"}
        </span>
      ),
    },
    {
      key: "priority",
      title: "优先级",
      dataIndex: "priority",
      dataType: "select",
      width: 100,
      options: [
        { label: "高", value: "高", pillVariant: "blue" },
        { label: "中", value: "中", pillVariant: "orange" },
        { label: "低", value: "低", pillVariant: "cyan" },
      ],
      render: (val) => {
        if (val === "高") return <FeishuPill variant="blue" showDot={false}>高</FeishuPill>;
        if (val === "中") return <FeishuPill variant="orange" showDot={false}>中</FeishuPill>;
        return <FeishuPill variant="cyan" showDot={false}>{val || "低"}</FeishuPill>;
      },
    },
    {
      key: "createdTime",
      title: "创建时间",
      dataIndex: "createdTime",
      dataType: "date",
      width: 130,
      render: (val) => (
        <span className="text-[14px] text-[#646A75] tabular-nums font-normal">
          {val || "—"}
        </span>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 200,
      render: () => (
        <div className="flex items-center justify-start gap-1">
          <FeishuActionLink>查看详情</FeishuActionLink>
          <FeishuActionLink>新增跟进记录</FeishuActionLink>
        </div>
      ),
    },
  ];

  // 订单列表 Columns
  const orderColumns: FeishuColumn<OrderItem>[] = [
    {
      key: "orderNo",
      title: "订单编号",
      dataIndex: "orderNo",
      dataType: "text",
      render: (val) => <FeishuCellMainSub main={val} />,
    },
    {
      key: "customerName",
      title: "客户",
      dataIndex: "customerName",
      dataType: "text",
      render: (val) => <span className="text-[14px] text-[#1F2329]">{val}</span>,
    },
    {
      key: "productName",
      title: "产品",
      dataIndex: "productName",
      dataType: "text",
      render: (val) => <span className="text-[14px] text-[#1F2329]">{val}</span>,
    },
    {
      key: "quantity",
      title: "数量",
      dataIndex: "quantity",
      align: "right",
      dataType: "number",
      render: (val) => <span className="text-[14px] tabular-nums">{val}</span>,
    },
    {
      key: "totalAmount",
      title: "金额",
      dataIndex: "totalAmount",
      align: "right",
      dataType: "number",
      render: (val) => <span className="font-mono text-[14px]">{Number(val).toLocaleString("zh-CN")}</span>,
    },
    {
      key: "status",
      title: "订单状态",
      dataIndex: "status",
      dataType: "select",
      options: [
        { label: "已完成", value: "COMPLETED", pillVariant: "green" },
        { label: "已付款", value: "PAID", pillVariant: "blue" },
        { label: "待付款", value: "PENDING", pillVariant: "orange" },
        { label: "已取消", value: "CANCELLED", pillVariant: "gray" },
      ],
      render: (val) => {
        if (val === "COMPLETED") return <FeishuPill variant="green">已完成</FeishuPill>;
        if (val === "PAID") return <FeishuPill variant="blue">已付款</FeishuPill>;
        if (val === "PENDING") return <FeishuPill variant="orange">待付款</FeishuPill>;
        return <FeishuPill variant="gray">已取消</FeishuPill>;
      },
    },
    {
      key: "ownerName",
      title: "负责人",
      dataIndex: "ownerName",
      dataType: "user",
      render: (val) => <FeishuAvatar name={val} />,
    },
    {
      key: "actions",
      title: "操作",
      render: () => (
        <div className="flex items-center justify-start gap-1">
          <FeishuActionLink>查看详情</FeishuActionLink>
          <FeishuActionLink>修改订单</FeishuActionLink>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-4">
      {/* 顶部筛选卡片 (1:1 飞书 CRM 筛选区，大圆角 rounded-[12px]) */}
      <div className="rounded-[12px] border border-[#DEE0E3] bg-white p-3 shadow-2xs flex flex-wrap items-center justify-between gap-4 select-none">
        <div className="flex items-center gap-3">
          <span className="text-[14px] text-[#646A75] font-medium">优先级</span>
          <div className="flex items-center rounded-[6px] bg-[#F2F3F5] p-0.5 text-[13px]">
            {["全部", "高", "中", "低"].map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => {
                  setPriorityFilter(p);
                  setCurrentPage(1);
                }}
                className={`px-3 py-1 rounded-[4px] font-medium transition-all cursor-pointer ${
                  priorityFilter === p
                    ? "bg-white text-[#1F2329] shadow-2xs font-semibold"
                    : "text-[#646A75] hover:text-[#1F2329]"
                }`}
              >
                {p}
              </button>
            ))}
          </div>
        </div>

        {/* 1:1 飞书下拉选择器 (商机阶段选择与联动过滤) */}
        <div className="flex items-center gap-2.5">
          <span className="text-[14px] text-[#646A75] font-medium">商机阶段</span>
          <FeishuSelect
            options={STAGE_OPTIONS}
            value={stageFilter}
            onChange={(val) => {
              setStageFilter(val || "ALL");
              setCurrentPage(1);
            }}
            placeholder="请选择商机阶段"
            size="md"
            className="w-[170px]"
          />
        </div>
      </div>

      {/* 1:1 飞书官方 Tab 栏扩展表格 (带平滑滑动动画指示条，支持任意扩展而样式不乱) */}
      <FeishuDataTable
        tabs={[
          { key: "opportunities", label: "商机列表", count: filteredOpportunities.length },
          { key: "orders", label: "订单管理", count: sampleOrders.length },
        ]}
        activeTabKey={activeTab}
        onTabChange={(k) => {
          setActiveTab(k);
          setCurrentPage(1);
        }}
        columns={activeTab === "opportunities" ? (oppColumns as FeishuColumn<any>[]) : (orderColumns as FeishuColumn<any>[])}
        data={activeTab === "opportunities" ? filteredOpportunities : sampleOrders}
        rowKey={activeTab === "opportunities" ? "id" : "orderId"}
        selectable={activeTab === "orders"}
        addButtonText={activeTab === "opportunities" ? "+ 新增商机" : "+ 新增订单"}
        onAdd={() => alert(activeTab === "opportunities" ? "新增商机" : "新建订单")}
        pagination={{
          current: activeTab === "opportunities" ? currentPage : 1,
          total: activeTab === "opportunities" ? filteredOpportunities.length : sampleOrders.length,
          pageSize: activeTab === "opportunities" ? pageSize : 10,
          onChange: (p, s) => {
            if (activeTab === "opportunities") {
              setCurrentPage(p);
              if (s && s !== pageSize) {
                setPageSize(s);
                setCurrentPage(1);
              }
            }
          },
        }}
      />
    </div>
  );
};
