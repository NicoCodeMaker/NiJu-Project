package com.example.niju_project

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FavoritosAdapter(
    private val context: Context,
    private val favoritosList: List<String>
) : RecyclerView.Adapter<FavoritosAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconFavorito: ImageView = view.findViewById(R.id.iconFavorito)
        val nombreFavorito: TextView = view.findViewById(R.id.nombreFavorito)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_favorito, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val nombre = favoritosList[position]
        holder.nombreFavorito.text = nombre
        // Si quisieras íconos diferentes según el contexto, podrías agregarlos aquí.
    }

    override fun getItemCount(): Int = favoritosList.size
}
