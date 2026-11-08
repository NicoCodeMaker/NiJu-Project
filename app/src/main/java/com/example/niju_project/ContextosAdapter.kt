package com.example.niju_project

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.niju_project.data.model.ContextProgress
import com.example.niju_project.ui.scene.ContextSceneView
import com.example.niju_project.ui.scene.ContextTheme
import com.example.niju_project.ui.scene.SceneUi
import com.google.android.material.progressindicator.LinearProgressIndicator

/** Tarjeta de contexto: escena animada, nombre, lecciones superadas y barra de avance con el color del contexto. */
class ContextosAdapter(
    private var contexts: List<ContextProgress>,
    /** Ancho fijo (dp) para listas horizontales; null = ocupa la celda de la cuadrícula. */
    private val fixedWidthDp: Int? = null,
    private val onClick: (ContextProgress) -> Unit
) : RecyclerView.Adapter<ContextosAdapter.ContextoViewHolder>() {

    fun updateList(newList: List<ContextProgress>) {
        contexts = newList
        notifyDataSetChanged()
    }

    class ContextoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val escena: ContextSceneView = itemView.findViewById(R.id.scene_contexto)
        val nombre: TextView = itemView.findViewById(R.id.tv_contexto_nombre)
        val lecciones: TextView = itemView.findViewById(R.id.tv_contexto_lecciones)
        val barra: LinearProgressIndicator = itemView.findViewById(R.id.pb_contexto)
        val hecho: TextView = itemView.findViewById(R.id.tv_contexto_hecho)
        val examen: TextView = itemView.findViewById(R.id.tv_contexto_examen)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContextoViewHolder =
        ContextoViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_contexto, parent, false).also { v ->
                fixedWidthDp?.let { w ->
                    v.layoutParams = v.layoutParams.apply {
                        width = (w * parent.resources.displayMetrics.density).toInt()
                    }
                }
            }
        )

    override fun onBindViewHolder(holder: ContextoViewHolder, position: Int) {
        val c = contexts[position]
        val ctx = holder.itemView.context
        val done = c.lessonsTotal > 0 && c.lessonsCompleted >= c.lessonsTotal
        holder.nombre.text = c.name
        val theme = ContextTheme.of(c.slug)
        holder.escena.slug = c.slug
        SceneUi.title(holder.nombre, theme)
        SceneUi.tint(holder.barra, theme)
        holder.examen.setTextColor(SceneUi.accentColor(ctx, theme))
        holder.examen.visibility = if (c.examPassed || c.examUnlocked) View.VISIBLE else View.GONE
        holder.examen.text = ctx.getString(if (c.examPassed) R.string.context_exam_passed else R.string.context_exam_ready)
        holder.lecciones.text = ctx.resources.getQuantityString(
            R.plurals.context_lessons_progress, c.lessonsTotal, c.lessonsCompleted, c.lessonsTotal
        )
        holder.barra.max = 100
        holder.barra.progress = if (c.lessonsTotal == 0) 0 else c.lessonsCompleted * 100 / c.lessonsTotal
        // El estado "completado" no depende solo del color: lleva marca ✓ y texto.
        holder.hecho.visibility = if (done) View.VISIBLE else View.GONE
        holder.itemView.contentDescription = ctx.getString(
            if (done) R.string.context_card_desc_done else R.string.context_card_desc,
            c.name, c.lessonsCompleted, c.lessonsTotal
        )
        holder.itemView.setOnClickListener { onClick(c) }
    }

    override fun getItemCount(): Int = contexts.size
}
