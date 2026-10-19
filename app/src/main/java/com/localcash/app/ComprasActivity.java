package com.localcash.app;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.localcash.app.data.CompraRepository;
import com.localcash.app.model.Compra;

/** Planificación local de compras. */
public final class ComprasActivity extends AppCompatActivity {
    private CompraRepository repository;
    private EditText nombre, cantidad, precio;
    private LinearLayout lista;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_compras);
        repository = new CompraRepository(this); nombre = findViewById(R.id.input_shopping_name);
        cantidad = findViewById(R.id.input_shopping_quantity); precio = findViewById(R.id.input_shopping_price); lista = findViewById(R.id.list_shopping);
        findViewById(R.id.button_add_shopping).setOnClickListener(view -> agregar()); cargar();
    }
    private void agregar() {
        String texto = nombre.getText().toString().trim(); int unidades; long estimado;
        try { unidades = Integer.parseInt(cantidad.getText().toString()); } catch (NumberFormatException e) { unidades = 0; }
        try { String precioTexto = precio.getText().toString().trim(); estimado = precioTexto.isEmpty() ? 0 : Long.parseLong(precioTexto); } catch (NumberFormatException e) { estimado = -1; }
        if (texto.isEmpty() || unidades <= 0 || estimado < 0) { Toast.makeText(this, R.string.error_shopping_fields, Toast.LENGTH_SHORT).show(); return; }
        repository.agregar(texto, unidades, estimado); nombre.setText(""); cantidad.setText(""); precio.setText(""); cargar();
    }
    private void cargar() {
        lista.removeAllViews();
        for (Compra compra : repository.obtenerTodas()) {
            CheckBox item = new CheckBox(this); item.setText(getString(R.string.shopping_item_format, compra.getNombre(), compra.getCantidad(), compra.getPrecioEstimado()));
            item.setChecked(compra.isCompletada()); item.setTextColor(getColor(R.color.lc_text_primary));
            item.setOnCheckedChangeListener((button, checked) -> repository.marcarCompletada(compra.getId(), checked)); lista.addView(item);
        }
        if (lista.getChildCount() == 0) { TextView vacio = new TextView(this); vacio.setText(R.string.shopping_empty); lista.addView(vacio); }
    }
}
