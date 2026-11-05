# Sun & Ice Cube — Fabric 1.21.11

Mod id: `sunmod` · Mappings: Mojang oficiais · Java 21

## Como compilar
```
./gradlew build          # (Windows: gradlew.bat build)
```
O .jar fica em `build/libs/` (use o que **não** termina em `-sources`).
Coloque em `.minecraft/mods/` junto com o **Fabric API 0.141.6+1.21.11** (ou mais novo para 1.21.11).

## Como usar
* Aba **Ferramentas e utilidades** do criativo (ou `/give @s sunmod:sun_vanilla` e `/give @s sunmod:ice_cube 64`).
* **Sun**: clique direito num bloco → o Sol (50x50x50 blocos) aparece e fica parado no ar.
  Também funciona `/summon sunmod:sun_vanilla ~ ~ ~`.
  Só recebe dano do projétil Ice Cube (mãos, espadas, flechas, explosões etc. são ignorados; `/kill` remove).
* **Ice Cube**: segure o botão direito (carrega como arco, 1 s = força máxima) e solte para arremessar.

## Ajustes rápidos
| O quê | Onde |
|---|---|
| Vida do Sol (padrão 1000 = 10x a original) | `SunEntity.MAX_HEALTH` |
| Dano por Ice Cube (padrão 5 → 200 acertos) | `IceCubeProjectile.DAMAGE` |
| Tamanho do Sol | `ModEntities.SUN` (`.sized(50, 50)`) (a malha já foi convertida para 50 blocos no `.mesh`) |

## Barreira do Sol (orbe holográfico)
* A cada **5 minutos** (início → início) o Sol é envolvido por uma barreira translúcida (raio 27) com a textura de colmeia azul neon,
  que fica ativa por **2 minutos**; nesse tempo o Ice Cube estilhaça nela e o Sol **não recebe dano**. Depois some e o Sol volta a ser danificado.
* Aparece com fade-in (1 s), gira devagar e pisca nos últimos 10 s como aviso; fade-out de 2 s.
* O 1º ciclo conta a partir do momento em que o Sol é colocado. O estado do ciclo é salvo no mundo.
* Ajustes: `SunEntity.SHIELD_INTERVAL_TICKS`, `SHIELD_DURATION_TICKS`, `SHIELD_RADIUS`.
* Textura: `python tools_gen_shield_texture.py` (usa `source_models/escudo_energia_colmeia.png` com projeção senoidal, sem emenda e sem espremer nos polos; ajuste `FILL_ALPHA` para o vidro ficar mais/menos visível).
* Sem Z-fighting: `ShieldMesh` desenha só os quads virados para a câmera (uma camada por pixel).
* Código: `SunEntity` (ciclo/dano), `IceCubeProjectile` (colisão), `SunRenderer` + `ShieldMesh` (visual).

## Arquivos de origem
* `sun_vanilla.bbmodel` → `src/client/resources/assets/sunmod/models/sun_vanilla.mesh` (3 esferas, escala x40)
* `ice_cube.bbmodel` → `assets/sunmod/models/item/ice_cube.json` (2 cubos)

## Outline (contorno)
Os modelos `*_1.bbmodel` (pasta `source_models/`) usam *casco invertido*: as camadas "outline" têm as faces viradas para dentro.
* Sol: `SunRenderer` usa `RenderTypes.entityCutout` (com culling) para só aparecer a borda das esferas externas.
* Ice Cube: o 2º elemento de `models/item/ice_cube.json` é um cubo invertido (`from` > `to`) 0,25px maior.

## Machine Ice e Upgrader (sem craft)
Aba **Ferramentas e utilidades** do criativo, ou `/give @s sunmod:machine_ice`, `/give @s sunmod:upgrader`.
Clique direito em um bloco para colocar (a frente do modelo vira para você). **Soco** remove e devolve o item.
* **Machine Ice**: botão direito = recebe **1** Ice Cube por vez (espera de 1 s entre usos), até o limite de **64** Ice Cubes
  no inventário. `MachineIceEntity.ICE_PER_USE` / `COOLDOWN_TICKS` e `IceCubeItem.MAX_IN_INVENTORY`.
* **Upgrader**: só decorativo por enquanto (o botão direito não faz nada).

## UI Ice (HUD)
Canto inferior direito: ícone do Ice Cube + barra com "tenho/limite". A barra azul enche conforme os Ice Cubes no inventário
(0/64 vazia → 64/64 cheia). Código em `client/IceHud.java`; texturas em alta resolução (4 px por unidade de GUI, com `.png.mcmeta` blur) em `assets/sunmod/textures/gui/`.

## Armas (Gun Ice, Lazer Ice, Gravity Gun)
Aba **Ferramentas e utilidades** do criativo, ou `/give @s sunmod:gun_ice`, `sunmod:lazer_ice`, `sunmod:gravity_gun`.
Os modelos vêm de `source_models/*.bbmodel` convertidos por `tools_convert_weapons.py` (outlines = casco invertido, como no Ice Cube).

| Arma | Dano no Sol | Dinheiro por acerto | Como atira |
|---|---|---|---|
| Ice Cube (arremesso) | 5 | $3 | segurar para carregar e soltar |
| **Gun Ice** | 10 (2x) | $6 (2x) | **sem carregar**, 4 tiros/s enquanto o botão direito estiver apertado (projétil de gelo rápido) |
| **Lazer Ice** | 20 (4x) | $12 (4x) | **sem carregar**, raio instantâneo, 5 pulsos/s enquanto o botão direito estiver apertado |
| **Gravity Gun** | — | — | só existe como item por enquanto, não faz nada |

* **Munição:** Gun Ice e Lazer Ice gastam **1 Ice Cube por disparo** (`AutoFireItem.AMMO_PER_SHOT`; coloque 0 para munição infinita). No criativo não gasta.
* Ajustes: `GunIceItem.DAMAGE / COIN_VALUE / FIRE_INTERVAL_TICKS`, `LazerIceItem.DAMAGE / COIN_VALUE / FIRE_INTERVAL_TICKS / RANGE`.
* Os dois respeitam a barreira do Sol (não causam dano enquanto ela estiver ativa). O dano passa por `SunEntity.applyWeaponDamage`.

## Hitbox detalhada
* Máquinas: `data/sunmod/hitboxes/*.bin` tem 1 caixa orientada por cubo do .bbmodel (com rotação, inflate e outlines).
  Clique direito e soco só valem se o olhar do jogador acertar uma dessas caixas (`DetailedHitbox`, `StationEntity.isLookedAt`).
* Sol: a hitbox é a esfera externa (raio 25 = outline). O projétil testa a esfera diretamente (`SunEntity.hitsSegment`),
  porque o Minecraft só procura entidades perto da posição delas e não achava um Sol de 50 blocos.
* Para regenerar malha/hitbox de um .bbmodel novo use o script de conversão (ver histórico do projeto).

## Sun Temperature, Money Coins e Money Coin (moeda)
* **Sun Temperature** (HUD, canto superior direito): a temperatura é a vida do Sol (144.982 °C = 100 %). Cada Ice Cube que acerta
  faz o número e a % caírem. Só aparece quando há um Sol por perto (até 400 blocos). `SunEntity.MAX_TEMPERATURE`.
* **Money Coin** (entidade `sunmod:money_coin`): nasce na superfície do Sol, no ponto do acerto, a cada dano; toca a animação
  `spawn` do .bbmodel (giro 360°, subida, escala 0 → 1,5 → 1), voa até o jogador que acertou e some ao encostar,
  somando $3 (`MoneyCoinEntity.VALUE`). Enquanto voa deixa um rastro branco fino e suave. Tamanho na tela: `MoneyCoinRenderer.COIN_SCALE`.
* **Money Coins** (HUD, canto inferior esquerdo): "$00". Não é item: é o anexo `ModAttachments.MONEY` (salvo, mantido ao morrer,
  sincronizado só com o próprio jogador).
* **Ícones HD** dos itens (Ice Cube, Sun, Machine Ice, Upgrader): `textures/item/*_icon.png` (256x256). O Ice Cube usa o ícone HD só
  no inventário; na mão, no chão e voando continua o modelo 3D.
* O Upgrader usa a textura nova (`upgrader_1.png`).
