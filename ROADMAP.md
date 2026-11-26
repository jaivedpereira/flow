# Flow — Roadmap v1.6 → v2.0

**Status:** proposta para aprovação
**Autor:** Zyon · 24/10/2026
**Base:** v1.5 (versionCode 8)

---

## O que mudou desde o último plano

Li os comentários mais recentes do @reviewlord. Ele reportou **três coisas que
continuam quebradas** depois da v1.5:

| issue | comentário dele | tradução |
|---|---|---|
| #6 | *"pinch to zoom still not working"* | a pinça não funciona — só o botão |
| #7 | *"the preview still need to wait / only updated after you release the slider. not while seeking"* | o slider só atualiza ao soltar |
| #2 | *"dragging text is still laggy"* | arrastar texto trava |

Isso muda a prioridade. **Não faz sentido entregar camadas com o editor travando.**

---

## Diagnóstico dos três

### Pinça — o gesto é engolido
O `detectTransformGestures` está no mesmo `Box` que o detector de toque, declarado
**antes** dele. O detector de toque chama `awaitRelease()` no `onPress`, que segura
o primeiro dedo até soltar. Os dois pointers da pinça nunca chegam ao transform.

**Correção:** remover `onPress`/`awaitRelease`, deixar só `onLongPress` para o
"segurar e comparar", e reordenar os `pointerInput`. ~30 min.

### Slider — o render se cancela antes de terminar
O `applyFinish` é disparado pelo `LaunchedEffect` a cada mudança de valor. Cada
disparo **cancela o anterior**. Se um render leva mais que o intervalo entre ticks
(~16 ms), nenhum completa — só o último, quando o dedo para.

O `applyFinish` copia o bitmap e varre 645 mil pixels com 4 chamadas a `Random`
por pixel. Não é rápido o bastante.

**Correção:** trocar o cancelamento por *latest-wins* com `conflate` num canal
próprio, mais pré-gerar o padrão de ruído num tile em vez de chamar `Random` por
pixel. ~2 h.

### Texto — mesmo problema, mais o custo do overlay
Arrastar texto dispara o mesmo efeito barato, com o mesmo cancelamento. Somado a
isso, cada frame recompõe `Overlays.apply()` inteiro.

**Correção:** mesma do slider, mais cachear a composição de texto enquanto só a
posição muda. ~1 h.

---

## v1.6 — "editor que não trava"

**Objetivo:** os três bugs acima fechados, mais o que foi pedido.

| # | item | esforço | por quê |
|---|---|---|---|
| 1 | Pinça funcionando | 30 min | reportado 2× |
| 2 | Slider com update contínuo | 2 h | reportado 2× |
| 3 | Arrastar texto sem travar | 1 h | reportado |
| 4 | **Camadas** (lock / hide / reordenar) | 1-2 dias | pedido seu |
| 5 | Toque duplo no slider = reset | 10 min | padrão, trivial |

**Camadas — como vou fazer**

Hoje existem uma camada de texto e um array de adesivos soltos, sem ordem nem
estado. Vou introduzir:

```kotlin
data class Layer(
    val id: Long,
    val kind: LayerKind,   // TEXT | STICKER | ADJUSTMENT
    val name: String,
    val visible: Boolean,
    val locked: Boolean,
    val z: Int,
)
```

`EditState.layers` passa a ser a fonte da verdade, e os campos antigos viram
derivados para não quebrar os projetos salvos.

A lista mostra cada camada com olho e cadeado, e arrastar reordena o `z`.
Ajustes (cubo, efeito, curvas) também viram camada — é o que o Lightroom faz e o
que faz a lista valer a pena.

**Pronto quando:** os três bugs fechados, camadas funcionando, e uma foto com
3 camadas sobrepostas exporta na ordem certa.

---

## v1.7 — "o que o Lightroom tem e o Flow não"

Analisei o APK do Lightroom hoje. O que vale copiar:

| # | item | esforço | retorno |
|---|---|---|---|
| 1 | **Máscara de intervalo** (por luminância e por cor) | 4 h | alto |
| 2 | **Textura** — 3º slider de detalhe, raio médio | 2 h | alto |
| 3 | Avisos de clipping no histograma | 2 h | médio |
| 4 | Copiar/colar ajustes entre fotos | 3 h | alto em série |
| 5 | HSL por zona tonal (sombras/meios/altas) | 6 h | médio-alto |

**Máscara de intervalo primeiro.** É o maior ganho por hora: usa o `MaskData` que
já existe, só muda como o grid é preenchido. Permite "vinheta só nos escuros",
"saturar só os verdes".

---

## v1.8 — "qualidade de render"

| # | item | esforço | por quê |
|---|---|---|---|
| 1 | **Clarity via pirâmide Laplaciana** | 8 h | elimina halos do unsharp atual |
| 2 | Pipeline em espaço linear | 6 h | correção matemática do blend |
| 3 | Blur gaussiano separável | 3 h | vinheta e máscaras mais suaves |
| 4 | 20 testes do motor | 4 h | destrava refactor sem medo |

**Laplaciana por último de propósito.** É o maior salto visual, mas mexe no
coração do render — e acabamos de sair de três releases consertando bugs ali.
Estabiliza primeiro.

---

## v2.0 — "o que ninguém espera de um app grátis"

| item | esforço |
|---|---|
| Presets em pilha (cubo + efeito + curva como receita única) | 3 dias |
| Importar `.acv` (curvas do Photoshop) | 1 dia |
| Export de camadas separadas | 2 dias |
| Localização EN completa + 2 idiomas extras | 1 dia |
| Acessibilidade (TalkBack navegável) | 2 dias |

---

## Fora de escopo, e por quê

| feature do Lightroom | por que não |
|---|---|
| Remover (IA generativa) | precisa de servidor. Contra o princípio do Flow |
| Selecionar sujeito / céu | idem |
| Perfil adaptável | modelo de IA baixado da Adobe |
| Perfil de lente | exige base de milhares de lentes medidas |
| Sincronização em nuvem | contra o princípio do Flow |
| Vídeo, Reels, Timelapse | outro produto |

---

## Cronograma sugerido

```
v1.6   bugs + camadas          ~2-3 dias
v1.7   máscara intervalo etc.  ~2 dias
v1.8   qualidade de render     ~3 dias
v2.0   diferenciais            ~1 semana
```

Sem prazo fixo. Prefiro entregar estável a entregar rápido e quebrar.

---

## O que eu preciso decidir com você

1. **Camadas incluem ajustes?** (cubo, efeito, curvas como camada) — é o que faz a
   lista valer a pena, mas é mais trabalho
2. **A ordem v1.6 está certa?** bugs primeiro, camadas depois
3. **Algum item do v1.7 ou v1.8 que você quer adiantar?**
