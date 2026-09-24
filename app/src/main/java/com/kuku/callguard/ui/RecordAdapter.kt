package com.kuku.callguard.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.kuku.callguard.R

/** 拦截记录列表适配器：电话 / 短信 / 广告三个标签页共用，icon 随标签页切换 */
class RecordAdapter(private val context: Context) : BaseAdapter() {

    private val items = mutableListOf<RecordItem>()
    private val inflater = LayoutInflater.from(context)

    /** 当前标签页对应的类型图标 */
    var icon: String = "📞"

    fun submit(list: List<RecordItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): RecordItem = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    private class VH(view: View) {
        val tvIcon: TextView = view.findViewById(R.id.tvIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvReason: TextView = view.findViewById(R.id.tvReason)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: VH
        if (convertView == null) {
            view = inflater.inflate(R.layout.item_record, parent, false)
            holder = VH(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as VH
        }
        val item = items[position]
        holder.tvIcon.text = icon
        holder.tvTitle.text = item.title
        holder.tvReason.text = item.reason
        holder.tvSubtitle.text = item.subtitle
        holder.tvTime.text = item.time
        return view
    }
}
