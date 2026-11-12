# Backend del chatbot en Cloudflare

Este Worker es independiente del servidor Kotlin de Render. Usa el JSON de
`../src/main/resources/chatbot-conocimiento.json` como fuente de conocimiento.
`npm run build` incorpora su versión actual al Worker; después de editar el JSON
hay que volver a construir y desplegar. No consulta GitHub ni la agenda en vivo.

## Preparación

Desde esta carpeta, con Node.js 22 o superior:

```powershell
npm ci
npm test
npm run check
npx wrangler login
```

El inicio de sesión abre Cloudflare en el navegador. Selecciona la cuenta que
contiene `vetclinic-chatbot.garridofranciscojose01.workers.dev`.
Si hay varias cuentas, configura `account_id` en `wrangler.jsonc`.

Antes de desplegar, revisa `ALLOWED_ORIGINS` en `wrangler.jsonc`: debe contener
los orígenes exactos de tu frontend, separados por comas y sin barra final.
Los dominios incluidos proceden de la configuración del proyecto; quita los
que ya no utilices. No uses `*`. CORS limita navegadores, no autentica clientes.

`GROQ_MODEL` permite cambiar el modelo sin editar el handler. Por defecto usa
`openai/gpt-oss-20b` mediante Groq; verifica que esté disponible en tu cuenta
y mantén el plan gratuito. No activa facturación de OpenAI ni requiere su clave.

## Publicación

```powershell
npm run deploy
```

Esto actualiza el Worker existente llamado `vetclinic-chatbot` y crea los dos
bindings de rate limiting. No despliega el servidor Kotlin ni modifica Render.
Los namespace IDs de los límites deben ser únicos en tu cuenta; cambia los dos
valores en la configuración si ya se usan para otra aplicación.

En Cloudflare → Worker → Settings → Variables and Secrets, guarda un secreto
llamado `GROQ_API_KEY` con la clave de Groq. Si ya existe, no necesitas duplicarlo.
Alternativamente puedes usar `npx wrangler secret put GROQ_API_KEY`, que solicita
el valor de manera interactiva. Nunca lo escribas en el repositorio o el chat.

## Verificar

Abre la dirección pública o `/health`: debe mostrar `service`, `status`,
`knowledgeVersion` y `chatEndpoint`. Este chequeo no confirma el acceso a Groq.

Para probar una pregunta real desde PowerShell:

```powershell
$chatBody = @{ message = '¿Cuánto cuesta la consulta general?'; history = @() } | ConvertTo-Json
Invoke-RestMethod -Uri 'https://vetclinic-chatbot.garridofranciscojose01.workers.dev/api/chat' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($chatBody))
```

La respuesta es `{ "answer": "...", "knowledgeVersion": 7 }`.
Los errores devuelven `{ "error": { "code": "...", "message": "..." } }`.
Si ves `NOT_CONFIGURED`, revisa el secreto y los bindings. Si ves
`PROVIDER_UNAVAILABLE`, verifica en Groq que la clave y el modelo estén habilitados.
`PROVIDER_LIMIT` indica cuota agotada; no se reintenta automáticamente.

## Contrato para Angular

`POST /api/chat`, con `Content-Type: application/json`:

```json
{
  "message": "¿Y puedo cancelar sin costo?",
  "history": [
    { "role": "user", "content": "¿Cómo agendo una cita?" },
    { "role": "assistant", "content": "Ingresa al sistema y abre Mis Citas." }
  ]
}
```

Admite hasta 1200 caracteres por mensaje y los últimos 6 mensajes del historial,
excluyendo la pregunta actual. El frontend deberá mostrar las respuestas como
texto, manejar errores y ofrecer las FAQ de respaldo; esa interfaz es otro paso.
No envíes contraseñas, documentos ni historias clínicas. Las preguntas se envían
a Groq para procesarlas; el Worker no las guarda ni las registra en logs.

Límites aproximados: 6 solicitudes por minuto por IP y 20 en total por ubicación
de Cloudflare. Personas de una misma red comparten cupo. No son un límite global
estricto ni sustituyen las cuotas de Groq. Tiempo máximo para Groq: 20 segundos.

## Desarrollo local

Copia `.dev.vars.example` como `.dev.vars`, reemplaza el valor solo localmente
y ejecuta `npm run dev`. `.dev.vars` está excluido de Git. No uses una clave real
en las pruebas automatizadas: todas las respuestas de Groq allí son simuladas.

Referencias: https://console.groq.com/docs/models y
https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/

## Ahorro de tokens y respaldo

Las intenciones frecuentes explícitas (horarios, ubicación, registro, citas y catálogo) se responden con texto aprobado del JSON sin llamar a Groq. Las preguntas ambiguas o sobre detalles pasan al modelo, con contexto seleccionado y solo los últimos dos mensajes, hasta 600 caracteres cada uno.

La respuesta reserva hasta 700 tokens y usa razonamiento bajo. GROQ_FALLBACK_MODELS configura modelos alternativos separados por comas; actualmente gpt-oss-120b. Ante 429 o errores temporales 502/503/504 se prueba el siguiente modelo, hasta tres intentos y con un presupuesto total de 19 segundos. Se respeta Retry-After con un enfriamiento local por modelo. Esto no evade ni elimina cuotas compartidas y el enfriamiento no se comparte entre instancias.

El formato permite viñetas en servicios y precios y pasos numerados en los procesos. Tablas y rutas técnicas siguen prohibidas. Qwen 3.8 27B es el tercer respaldo, incluyendo casos donde Groq devuelve 403 o 404 por acceso o disponibilidad del modelo. La respuesta técnica incluye usage con los tokens de entrada, salida y total reportados por Groq; las respuestas locales indican total_tokens: 0. No se muestran estos metadatos en el chat.
