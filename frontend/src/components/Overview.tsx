import React, { useEffect, useState } from 'react';
import ReactECharts from 'echarts-for-react';
export function Overview() {
  const [stats, setStats] = useState({
    total: 0, completed: 0, avg_similarity: 0, high_similarity_alerts: 0,
    distribution: { high: 0, medium: 0, low: 0 },
    trend: { dates: [], counts: [], similarities: [] }
  });
  useEffect(() => {
    fetch('http://localhost:8000/dashboard-stats')
      .then(r => r.json())
      .then(d => {
        // Only set if we get valid data
        if (d && typeof d.total !== 'undefined') {
          setStats(d);
        }
      })
      .catch(e => console.error("Stats fetch error:", e));
  }, []);
  const lineOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: stats.trend?.dates || ['-', '-', '-', '-', '-', '-', '-'],
      axisLine: { lineStyle: { color: '#334155' } }
    },
    yAxis: [
      { type: 'value', axisLine: { show: false }, splitLine: { lineStyle: { color: '#1e293b' } } },
      { type: 'value', axisLine: { show: false }, splitLine: { show: false } }
    ],
    series: [
      {
        name: '任务数',
        type: 'line',
        smooth: true,
        data: stats.trend?.counts || [0, 0, 0, 0, 0, 0, 0],
        itemStyle: { color: '#3b82f6' },
        areaStyle: { color: 'rgba(59, 130, 246, 0.1)' }
      },
      {
        name: '平均相似度',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: stats.trend?.similarities || [0, 0, 0, 0, 0, 0, 0],
        itemStyle: { color: '#10b981' }
      }
    ]
  };
  const pieOption = {
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['60%', '80%'],
        avoidLabelOverlap: false,
        label: { show: false, position: 'center' },
        labelLine: { show: false },
        data: [
          { value: stats.distribution.high, name: '高相似度 (70-100%)', itemStyle: { color: '#ef4444' } },
          { value: stats.distribution.medium, name: '中相似度 (40-70%)', itemStyle: { color: '#f59e0b' } },
          { value: stats.distribution.low, name: '低相似度 (0-40%)', itemStyle: { color: '#10b981' } }
        ]
      }
    ]
  };
  return (
    <div className="bg-card rounded-xl p-6 border border-border h-auto">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-medium">今日概览</h3>
      </div>

      <div className="grid grid-cols-4 gap-4 mb-8">
        <div className="bg-muted/30 p-4 rounded-lg">
          <div className="text-sm text-muted-foreground mb-2">任务总数</div>
          <div className="text-3xl font-bold flex items-end gap-2">{stats.total}</div>
        </div>
        <div className="bg-muted/30 p-4 rounded-lg">
          <div className="text-sm text-muted-foreground mb-2">已完成</div>
          <div className="text-3xl font-bold flex items-end gap-2">{stats.completed}</div>
        </div>
        <div className="bg-muted/30 p-4 rounded-lg">
          <div className="text-sm text-muted-foreground mb-2">平均相似度</div>
          <div className="text-3xl font-bold flex items-end gap-2">{stats.avg_similarity}%</div>
        </div>
        <div className="bg-muted/30 p-4 rounded-lg border border-red-500/30">
          <div className="text-sm text-muted-foreground mb-2">高相似度预警</div>
          <div className="text-3xl font-bold flex items-end gap-2 text-red-500">{stats.high_similarity_alerts}</div>
          <div className="text-3xl font-bold flex items-end gap-2 text-red-500">{stats.distribution.high}</div>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-6">
        <div>
          <div className="flex gap-4 mb-2 text-sm text-muted-foreground">
            <span>趋势分析</span>
            <div className="flex gap-4 items-center">
              <div className="flex items-center gap-1"><div className="w-2 h-2 rounded-full bg-primary"></div>任务数</div>
              <div className="flex items-center gap-1"><div className="w-2 h-2 rounded-full bg-emerald-500"></div>平均相似度</div>
            </div>
          </div>
          <ReactECharts option={lineOption} style={{ height: '220px' }} />
        </div>
        <div>
          <div className="text-sm text-muted-foreground mb-2">相似度分布</div>
          <div className="flex items-center h-[220px]">
            <div className="w-1/2 h-full relative">
              <ReactECharts option={pieOption} style={{ height: '100%' }} />
              <div className="absolute inset-0 flex items-center justify-center flex-col pointer-events-none">
                <span className="text-3xl font-bold text-foreground">{stats.total}</span>
                <span className="text-xs text-muted-foreground">总任务</span>
              </div>
            </div>
            <div className="w-1/2 pl-4 flex flex-col gap-4">
              <div className="flex items-center gap-2 text-sm">
                <div className="w-3 h-3 rounded-full bg-red-500"></div>
                <div className="flex-1">高相似度 (70-100%)</div>
                <div className="text-muted-foreground">{stats.distribution.high} ({stats.total ? Math.round(stats.distribution.high / stats.total * 100) : 0}%)</div>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <div className="w-3 h-3 rounded-full bg-amber-500"></div>
                <div className="flex-1">中相似度 (40-70%)</div>
                <div className="text-muted-foreground">{stats.distribution.medium} ({stats.total ? Math.round(stats.distribution.medium / stats.total * 100) : 0}%)</div>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <div className="w-3 h-3 rounded-full bg-emerald-500"></div>
                <div className="flex-1">低相似度 (0-40%)</div>
                <div className="text-muted-foreground">{stats.distribution.low} ({stats.total ? Math.round(stats.distribution.low / stats.total * 100) : 0}%)</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}