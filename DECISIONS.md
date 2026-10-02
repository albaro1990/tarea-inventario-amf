# Decisiones de implementación

## Diseño

- `InventoryServiceImpl` guarda el estado en memoria con un `ConcurrentHashMap<String, ProductStock>`
  (SKU -> estado) y un índice auxiliar `Map<String orderId, String sku>` para que `confirm(orderId)`
  pueda localizar el SKU sin recorrer todo el inventario.
- `ProductStock` agrupa `totalUnits`, `soldUnits` (confirmados, nunca vuelven a stock) y las reservas
  activas. `available()` siempre se calcula como `totalUnits - soldUnits - reservado`.
- Las reglas por categoría (TTL y límite por pedido) viven en `CategoryPolicy`, separadas de
  `ProductCategory` porque ese enum pertenece al contrato y no se puede modificar.

## Supuestos

- **Expiración perezosa, no hay hilo de fondo.** Una reserva vencida no se "libera" en segundo plano;
  simplemente se ignora/purga la próxima vez que algo toca ese SKU (`reserve`, `confirm`, `addStock`,
  `available`), comparando `expiresAt` contra `clock.instant()`. Es coherente con que `Clock` se
  inyecta como dependencia (pensado para poder simular tiempo en tests) y evita introducir
  scheduler, hilos o estado adicional para un caso que no lo necesita: nadie puede comprar una unidad
  "reservada pero vencida" porque el cálculo de disponibilidad siempre la descarta primero.
- **`reserve` es idempotente por `orderId`.** El README dice explícitamente que la app reintenta el
  mismo pedido automáticamente si la conexión es lenta. Si `orderId` ya tiene una reserva activa para
  el mismo SKU, se devuelve esa misma reserva en vez de crear una nueva o fallar; así un reintento no
  descuenta stock dos veces. Asumo que los reintentos llegan con el mismo SKU/cantidad que el
  original (no se compara la cantidad del reintento contra la original). Si el mismo `orderId` llega
  con otro SKU, se trata como un error de programación (`IllegalStateException`), ya que el contrato
  dice "cada pedido reserva un solo producto".
- **El aviso de bajo stock es edge-triggered y solo se resetea con reabastecimiento explícito.**
  Se dispara una vez cuando `available(sku) <= 5` y no se repite mientras siga en ese rango. El flag
  se resetea únicamente en `addStock` cuando el nuevo disponible supera el umbral — no al expirar una
  reserva — porque el README habla de "reabastecer" como una acción del equipo de compras
  (`addStock`), no como la liberación natural de una reserva vencida.
- **`registerProduct` no se puede llamar dos veces para el mismo SKU.** No está en el contrato
  explícitamente, pero registrar dos veces normalmente indica un bug del llamador; lanza
  `IllegalArgumentException` en vez de sobrescribir en silencio.
- **Reutilizar un `orderId` después de confirmarlo no está soportado.** Asumo que cada `orderId` se
  usa una sola vez de punta a punta (reservas reintentadas y, como mucho, una confirmación). No hay
  protección contra reservar de nuevo con un `orderId` ya confirmado.

## Concurrencia

- Cada `ProductStock` es también la unidad de bloqueo: todas las operaciones sobre un SKU
  (`reserve`, `confirm`, `addStock`, `available`) se sincronizan sobre ese objeto, así que dos SKUs
  distintos nunca se bloquean entre sí, pero las reservas concurrentes del mismo SKU se serializan y
  nunca sobrevenden. Esto se ejercita en `InventoryConcurrencyTest` con 200 intentos concurrentes
  sobre 50 unidades.
- Queda una carrera teórica y de bajo impacto: si el mismo `orderId` reserva dos SKUs distintos en
  paralelo, ambos hilos podrían pasar la verificación de "orderId ya usado" antes de que cualquiera
  publique su resultado. Esto ya es un uso inválido del contrato (un pedido reserva un solo
  producto), así que no agregué un lock global para cubrirlo — hacerlo penalizaría el caso común
  (SKUs distintos en paralelo, que es justo lo que hay que escalar).

## Qué dejé fuera / qué cambiaría antes de producción

- **Persistencia y múltiples instancias.** El enunciado dice que esto migrará a base de datos y
  correrá en varias instancias; el `synchronized` por SKU solo sirve dentro de una JVM. En producción
  reemplazaría el estado en memoria por una tabla con bloqueo optimista (columna de versión) o un
  `UPDATE ... WHERE available >= ?` atómico, y quitaría el bloqueo en memoria.
- **Canales de alerta.** `StockAlertListener` hoy solo representa "algo pasa con el SKU que quedó
  bajo". El código no asume el canal (eso es responsabilidad del invocador/implementación real de la
  interfaz), pero si se agregan más canales convendría pasar por una cola de eventos en vez de una
  llamada síncrona dentro del bloqueo del SKU, para no bloquear reservas si el canal es lento.
  **Antes de producción revisaría esto**, ya que es un problema arquitectónico.
- **Categorías como enum fijo.** El README menciona que marketing crea categorías nuevas cada
  temporada; hoy cada categoría nueva requiere tocar `CategoryPolicy` y desplegar. Si esto
  pasa seguido, movería el límite a configuración o una tabla en base de datos pero no dejaria en codigo java.
- **Validación de entradas mínima.** No valido `sku`/`orderId` nulos o vacíos porque no estaba en el
  contrato ni en los tests; en un servicio real agregaría esa validación en el borde (API HTTP), no
  necesariamente aquí.
- **Sin observabilidad.** No hay métricas ni logs de reservas/expiraciones/ventas. Antes de producción
  agregaría métricas (reservas creadas, expiradas, confirmadas, denegadas por stock/límite) para medir
  sobreventa evitada y detectar categorías mal configuradas.
- **Sin auditoría histórica.** Las reservas expiradas se descartan sin dejar rastro. Si compras o
  soporte necesitan reconstruir qué pasó con un pedido, haría falta un log de eventos separado del
  estado operacional.

## Que agregue
- Adicione unit test para poder ir validando mi desarrollo mas alla de los resultados esperados por los unit test que ya estaban en el proyecto inciial.
