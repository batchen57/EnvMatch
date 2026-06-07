import { useEffect, useState } from 'react';
import ReactECharts from 'echarts-for-react';
import { 
  BarChart3, Activity, Cpu, HardDrive, TrendingUp, 
  ShieldAlert, PieChart, Info, Calendar, Sparkles,
  ArrowUpRight, ArrowDownRight, Zap
} from 'lucide-react';
import { cn } from '@/lib/utils';

export function ResultReport() {
  const [stats, setStats] = useState<any>({
    total: 0, completed: 0, failed: 0, avg_similarity: 0,
    total_tokens: 0, total_duration: 0, total_size: 0,
    avg_dimensions: { indoor_layout: 0, wall_floor_material: 0, furniture_fixtures: 0, window_door_style: 0, lighting_environment: 0 },
    models_summary: [],
    distribution: { high: 0, medium: 0, low: 0 },
    trend: { dates: [], counts: [], similarities: [], tokens: [] }
  });

  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetch('http://localhost:8888/dashboard-stats')
      .then(r => r.json())
      .then(d => {
        if (d && typeof d.total !== 'undefined') {
          setStats(d);
        }
        setLoading(false);
      })
      .catch(e => {
        console.error("Stats fetch error:", e);
        setLoading(false);
      });
  }, []);

  const radarOption = {
    backgroundColor: 'transparent',
    radar: {
      indicator: [
        { name: '室内布局', max: 100 },
        { name: '墙地材质', max: 100 },
        { name: '家具设施', max: 100 },
        { name: '门窗样式', max: 100 },
        { name: '光照环境', max: 100 }
      ],
      center: ['50%', '50%'],
      radius: '60%',
      shape: 'polygon',
      axisName: {
        color: '#94a3b8',
        fontSize: 10,
        fontWeight: '500',
      },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.05)' } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          stats.avg_dimensions.indoor_layout,
          stats.avg_dimensions.wall_floor_material,
          stats.avg_dimensions.furniture_fixtures,
          stats.avg_dimensions.window_door_style,
          stats.avg_dimensions.lighting_environment
        ],
        name: '环境维度分布',
        areaStyle: {
          color: {
            type: 'radial',
            x: 0.5, y: 0.5, r: 0.5,
            colorStops: [
              { offset: 0, color: 'rgba(59, 130, 246, 0.4)' },
              { offset: 1, color: 'rgba(59, 130, 246, 0.1)' }
            ]
          }
        },
        lineStyle: { color: '#3b82f6', width: 2, shadowBlur: 10, shadowColor: 'rgba(59, 130, 246, 0.5)' },
        symbol: 'circle',
        symbolSize: 4,
        itemStyle: { color: '#3b82f6' }
      }]
    }]
  };

  const modelBarOption = {
    backgroundColor: 'transparent',
    tooltip: { 
      trigger: 'axis',
      backgroundColor: 'rgba(15, 23, 42, 0.9)',
      borderColor: 'rgba(59, 130, 246, 0.2)',
      textStyle: { color: '#f8fafc' }
    },
    grid: { left: '3%', right: '3%', bottom: '5%', top: '15%', containLabel: true },
    xAxis: { 
      type: 'category', 
      data: stats.models_summary.map((m: any) => m.model_id.split('-').slice(0, 2).join('-')),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#64748b', fontSize: 10 }
    },
    yAxis: [
      { 
        type: 'value', 
        name: '任务数',
        nameTextStyle: { color: '#64748b', fontSize: 10 },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.05)' } },
        axisLabel: { color: '#64748b', fontSize: 10 }
      },
      { 
        type: 'value', 
        name: '相似度',
        max: 100,
        nameTextStyle: { color: '#64748b', fontSize: 10 },
        splitLine: { show: false },
        axisLabel: { color: '#64748b', fontSize: 10, formatter: '{value}%' }
      }
    ],
    series: [
      {
        name: '处理量',
        type: 'bar',
        data: stats.models_summary.map((m: any) => m.count),
        itemStyle: { 
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#3b82f6' },
              { offset: 1, color: '#1d4ed8' }
            ]
          },
          borderRadius: [4, 4, 0, 0] 
        },
        barWidth: '25%'
      },
      {
        name: '平均分',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: stats.models_summary.map((m: any) => m.avg_similarity),
        itemStyle: { color: '#10b981' },
        lineStyle: { width: 3, shadowBlur: 10, shadowColor: 'rgba(16, 185, 129, 0.3)' },
        symbol: 'circle',
        symbolSize: 8
      }
    ]
  };

  const trendOption = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '3%', bottom: '5%', top: '15%', containLabel: true },
    xAxis: { 
      type: 'category', 
      boundaryGap: false,
      data: stats.trend.dates,
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } },
      axisLabel: { color: '#64748b', fontSize: 10 }
    },
    yAxis: [
      { 
        type: 'value', 
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.05)' } },
        axisLabel: { color: '#64748b', fontSize: 10 }
      },
      { 
        type: 'value', 
        name: 'Tokens',
        position: 'right',
        splitLine: { show: false },
        axisLabel: { color: '#64748b', fontSize: 10 }
      }
    ],
    series: [
      {
        name: '每日任务',
        type: 'line',
        smooth: true,
        data: stats.trend.counts,
        itemStyle: { color: '#3b82f6' },
        lineStyle: { width: 3 },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(59, 130, 246, 0.2)' },
              { offset: 1, color: 'rgba(59, 130, 246, 0)' }
            ]
          }
        }
      },
      {
        name: 'Token消耗',
        type: 'bar',
        yAxisIndex: 1,
        data: stats.trend.tokens,
        itemStyle: { 
          color: 'rgba(139, 92, 246, 0.15)',
          borderRadius: [2, 2, 0, 0]
        },
        barWidth: '40%'
      }
    ]
  };

  const pieOption = {
    backgroundColor: 'transparent',
    series: [{
      type: 'pie',
      radius: ['55%', '75%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#0f172a', borderWidth: 2 },
      label: { show: false },
      data: [
        { value: stats.distribution.high, name: '高相似', itemStyle: { color: '#ef4444' } },
        { value: stats.distribution.medium, name: '中相似', itemStyle: { color: '#f59e0b' } },
        { value: stats.distribution.low, name: '低相似', itemStyle: { color: '#10b981' } }
      ]
    }]
  };

  if (loading) return (
    <div className="h-[600px] flex flex-col items-center justify-center gap-4 text-muted-foreground">
      <div className="w-12 h-12 border-4 border-primary/20 border-t-primary rounded-full animate-spin" />
      <div className="text-sm font-medium animate-pulse tracking-widest uppercase">Initializing Intelligence Report</div>
    </div>
  );

  return (
    <div className="max-w-[1400px] mx-auto space-y-8 pb-12 animate-in fade-in slide-in-from-bottom-4 duration-700">
      {/* Page Header */}
      <div className="flex justify-between items-end">
        <div>
          <div className="flex items-center gap-2 text-primary mb-1">
            <Sparkles className="w-4 h-4" />
            <span className="text-[10px] font-bold uppercase tracking-[0.2em]">Platform Intelligence</span>
          </div>
          <h2 className="text-3xl font-black tracking-tight text-foreground">结果报表 <span className="text-muted-foreground/30 font-light">Summary Report</span></h2>
        </div>
        <div className="flex gap-4 items-center bg-card/50 backdrop-blur border border-border p-1 rounded-xl">
          <button className="px-4 py-2 text-xs font-bold bg-primary text-primary-foreground rounded-lg shadow-lg shadow-primary/20 transition-all hover:scale-105 active:scale-95">导出PDF</button>
          <div className="h-6 w-px bg-border" />
          <div className="px-3 flex items-center gap-2 text-muted-foreground">
            <Calendar className="w-3.5 h-3.5" />
            <span className="text-[10px] font-medium uppercase">{new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })}</span>
          </div>
        </div>
      </div>

      {/* KPI Section */}
      <div className="grid grid-cols-4 gap-6">
        <StatCard 
          icon={<Zap className="w-5 h-5" />} 
          label="累计分析规模" 
          value={stats.total} 
          unit="Tasks" 
          accent="blue"
          trend="+12.5%"
          subValue={`成功率 ${(stats.completed / (stats.total || 1) * 100).toFixed(1)}%`}
        />
        <StatCard 
          icon={<Activity className="w-5 h-5" />} 
          label="全库平均相似度" 
          value={stats.avg_similarity} 
          unit="%" 
          accent="emerald"
          trend="-0.4%"
          subValue="Cross-model weighted"
        />
        <StatCard 
          icon={<Cpu className="w-5 h-5" />} 
          label="Token 总消耗" 
          value={(stats.total_tokens / 1000).toFixed(1)} 
          unit="K" 
          accent="purple"
          trend="+5.2%"
          subValue={`Avg ${(stats.total_tokens / (stats.total || 1)).toFixed(0)} / task`}
        />
        <StatCard 
          icon={<HardDrive className="w-5 h-5" />} 
          label="处理资源总量" 
          value={stats.total_duration.toFixed(0)} 
          unit="Sec" 
          accent="amber"
          trend="+8.1%"
          subValue={`${stats.total_size.toFixed(1)} MB processed`}
        />
      </div>

      {/* Main Analysis Area */}
      <div className="grid grid-cols-12 gap-8">
        {/* Left Col: Dimension */}
        <div className="col-span-4 flex flex-col">
          <section className="flex-1 bg-card/40 backdrop-blur border border-border rounded-2xl p-6 relative overflow-hidden group flex flex-col">
            <div className="absolute top-0 right-0 w-32 h-32 bg-primary/5 rounded-full -mr-16 -mt-16 blur-3xl group-hover:bg-primary/10 transition-colors" />
            <div className="flex justify-between items-center mb-6 shrink-0">
              <h4 className="text-xs font-bold uppercase tracking-widest text-muted-foreground flex items-center gap-2">
                <ShieldAlert className="w-3.5 h-3.5 text-primary" />
                环境维度偏好
              </h4>
              <Info className="w-3.5 h-3.5 text-muted-foreground/50" />
            </div>
            <div className="flex-1 min-h-[280px]">
              <ReactECharts option={radarOption} style={{ height: '100%', width: '100%' }} />
            </div>
            <div className="mt-6 space-y-3 shrink-0">
              {Object.entries(stats.avg_dimensions).slice(0, 3).map(([key, val]: any) => (
                <div key={key} className="flex items-center justify-between group/item">
                  <span className="text-[11px] text-muted-foreground group-hover/item:text-foreground transition-colors">{getDimLabel(key)}</span>
                  <div className="flex items-center gap-3 flex-1 px-4">
                    <div className="h-1 flex-1 bg-muted rounded-full overflow-hidden">
                      <div className="h-full bg-primary/60" style={{ width: `${val}%` }} />
                    </div>
                  </div>
                  <span className="text-xs font-mono font-bold text-primary">{val}</span>
                </div>
              ))}
            </div>
          </section>
        </div>

        {/* Right Col: Model Performance */}
        <div className="col-span-8 flex flex-col">
          <section className="flex-1 bg-card/40 backdrop-blur border border-border rounded-2xl p-6 relative flex flex-col">
            <div className="flex justify-between items-center mb-8 shrink-0">
              <div>
                <h4 className="text-xs font-bold uppercase tracking-widest text-muted-foreground flex items-center gap-2 mb-1">
                  <BarChart3 className="w-3.5 h-3.5 text-primary" />
                  模型效能矩阵
                </h4>
                <p className="text-[10px] text-muted-foreground italic">Comparing task volume vs. similarity outcome</p>
              </div>
              <div className="flex gap-4">
                <div className="flex items-center gap-1.5"><div className="w-2 h-2 rounded-full bg-primary" /> <span className="text-[10px] text-muted-foreground">处理量</span></div>
                <div className="flex items-center gap-1.5"><div className="w-2 h-2 rounded-full bg-emerald-500" /> <span className="text-[10px] text-muted-foreground">评分稳定性</span></div>
              </div>
            </div>
            <div className="flex-1 min-h-[360px]">
              <ReactECharts option={modelBarOption} style={{ height: '100%', width: '100%' }} />
            </div>
          </section>
        </div>
      </div>

      <div className="grid grid-cols-12 gap-8">
        {/* Left Col: Trend Analysis */}
        <div className="col-span-8 flex flex-col">
          <section className="flex-1 bg-card/40 backdrop-blur border border-border rounded-2xl p-6 flex flex-col">
            <div className="flex justify-between items-center mb-8 shrink-0">
              <h4 className="text-xs font-bold uppercase tracking-widest text-muted-foreground flex items-center gap-2">
                <TrendingUp className="w-3.5 h-3.5 text-primary" />
                业务趋势及资源负载
              </h4>
              <div className="bg-muted/50 rounded-lg p-1 flex gap-1">
                {['7D', '30D', 'ALL'].map(t => (
                  <button key={t} className={cn("px-3 py-1 text-[9px] font-bold rounded", t === '7D' ? "bg-card text-foreground shadow-sm" : "text-muted-foreground")}>{t}</button>
                ))}
              </div>
            </div>
            <div className="flex-1 min-h-[320px]">
              <ReactECharts option={trendOption} style={{ height: '100%', width: '100%' }} />
            </div>
          </section>
        </div>

        {/* Right Col: Distribution */}
        <div className="col-span-4 flex flex-col">
          <section className="flex-1 bg-card/40 backdrop-blur border border-border rounded-2xl p-6 overflow-hidden flex flex-col">
            <h4 className="text-xs font-bold uppercase tracking-widest text-muted-foreground mb-6 flex items-center gap-2 shrink-0">
              <PieChart className="w-3.5 h-3.5 text-primary" />
              相似度层级分布
            </h4>
            <div className="flex-1 flex flex-col justify-center">
              <div className="flex items-center gap-6">
                <div className="w-1/2 h-[200px] relative">
                  <ReactECharts option={pieOption} style={{ height: '100%', width: '100%' }} />
                  <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                    <span className="text-xl font-black text-foreground">{stats.total}</span>
                    <span className="text-[8px] text-muted-foreground uppercase font-bold tracking-tighter">Total</span>
                  </div>
                </div>
                <div className="w-1/2 space-y-4">
                  <DistItemCompact label="High" count={stats.distribution.high} color="bg-red-500" total={stats.total} />
                  <DistItemCompact label="Med" count={stats.distribution.medium} color="bg-amber-500" total={stats.total} />
                  <DistItemCompact label="Low" count={stats.distribution.low} color="bg-emerald-500" total={stats.total} />
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}

function StatCard({ icon, label, value, unit, accent, trend, subValue }: any) {
  const colors: any = {
    blue: "text-blue-500 bg-blue-500/10 border-blue-500/20",
    emerald: "text-emerald-500 bg-emerald-500/10 border-emerald-500/20",
    purple: "text-purple-500 bg-purple-500/10 border-purple-500/20",
    amber: "text-amber-500 bg-amber-500/10 border-amber-500/20",
  };

  const isUp = trend.startsWith('+');

  return (
    <div className="bg-card/60 backdrop-blur border border-border rounded-2xl p-6 hover:shadow-xl hover:shadow-primary/5 transition-all group overflow-hidden relative">
      {/* Decorative gradient */}
      <div className={cn("absolute -bottom-8 -right-8 w-24 h-24 blur-[40px] opacity-20 transition-opacity group-hover:opacity-40", 
        accent === 'blue' ? 'bg-blue-500' : accent === 'emerald' ? 'bg-emerald-500' : accent === 'purple' ? 'bg-purple-500' : 'bg-amber-500')} />
      
      <div className="flex justify-between items-start mb-6">
        <div className={cn("p-2.5 rounded-xl border", colors[accent])}>
          {icon}
        </div>
        <div className={cn("flex items-center gap-1 text-[10px] font-black px-2 py-0.5 rounded-full border", 
          isUp ? "text-emerald-500 bg-emerald-500/10 border-emerald-500/20" : "text-red-500 bg-red-500/10 border-red-500/20")}>
          {isUp ? <ArrowUpRight className="w-2.5 h-2.5" /> : <ArrowDownRight className="w-2.5 h-2.5" />}
          {trend}
        </div>
      </div>
      
      <div className="relative">
        <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest mb-1">{label}</p>
        <div className="flex items-baseline gap-2">
          <span className="text-3xl font-black text-foreground tabular-nums tracking-tighter">{value}</span>
          <span className="text-xs font-medium text-muted-foreground">{unit}</span>
        </div>
        <p className="text-[10px] text-muted-foreground mt-4 font-medium flex items-center gap-2">
          <span className="w-1 h-1 rounded-full bg-border" />
          {subValue}
        </p>
      </div>
    </div>
  );
}

function DistItemCompact({ label, count, color, total }: any) {
  const percent = total ? Math.round(count/total*100) : 0;
  return (
    <div className="group cursor-default">
      <div className="flex justify-between items-center mb-1.5">
        <span className="text-[10px] font-bold text-muted-foreground uppercase">{label}</span>
        <span className="text-[10px] font-mono text-foreground font-bold">{percent}%</span>
      </div>
      <div className="h-1.5 bg-muted rounded-full overflow-hidden flex">
        <div className={cn("h-full transition-all duration-1000", color)} style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}

function getDimLabel(key: string) {
  const labels: any = {
    indoor_layout: '室内布局',
    wall_floor_material: '墙地材质',
    furniture_fixtures: '家具设施',
    window_door_style: '门窗样式',
    lighting_environment: '光照环境'
  };
  return labels[key] || key;
}
