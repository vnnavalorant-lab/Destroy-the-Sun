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
* **Sun**: clique direito num bloco → o Sol (10x10x10 blocos) aparece e fica parado no ar.
  Também funciona `/summon sunmod:sun_vanilla ~ ~ ~`.
  Só recebe dano do projétil Ice Cube (mãos, espadas, flechas, explosões etc. são ignorados; `/kill` remove).
* **Ice Cube**: segure o botão direito (carrega como arco, 1 s = força máxima) e solte para arremessar.

## Ajustes rápidos
| O quê | Onde |
|---|---|
| Vida do Sol (padrão 100) | `SunEntity.MAX_HEALTH` |
| Dano por Ice Cube (padrão 5 → 20 acertos) | `IceCubeProjectile.DAMAGE` |
| Tamanho do Sol | `ModEntities.SUN` (`.sized(10, 10)`) (a malha já foi convertida para 10 blocos no `.mesh`) |

## Arquivos de origem
* `sun_vanilla.bbmodel` → `src/client/resources/assets/sunmod/models/sun_vanilla.mesh` (3 esferas, escala x8)
* `ice_cube.bbmodel` → `assets/sunmod/models/item/ice_cube.json` (2 cubos)
