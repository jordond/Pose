package io.github.akshaychordiya.pose.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.akshaychordiya.pose.Pose

/**
 * Showcase — `companion.previewSamples`.
 *
 * When you own the type, expose a `Sequence<T>` on its companion. Pose wires
 * it through `@PreviewParameter` — one preview per element. Empty / long-text /
 * discounted / edge-case variants all render side-by-side.
 */
data class Product(
    val name: String,
    val priceLabel: String,
    val badge: String?,
) {
    companion object {
        val previewSamples: Sequence<Product> = sequenceOf(
            Product(name = "Notebook",         priceLabel = "£9.99",   badge = null),
            Product(name = "Fountain pen",     priceLabel = "£24.00",  badge = "New"),
            Product(name = "Very long name that wraps onto two lines",
                                                priceLabel = "£120.00", badge = "-15%"),
        )
    }
}

@Pose
@Composable
fun ProductCard(product: Product, onTap: () -> Unit) {
    Card(Modifier.padding(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(product.name)
            Text(product.priceLabel)
            product.badge?.let { Text(it) }
        }
    }
}
