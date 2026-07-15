#!/usr/bin/env python3
"""Génère les loot tables « drop self » des blocs custom MineAzur.

POURQUOI : en MC moderne (contrairement à 1.2.5), un bloc ne droppe QUE ce que sa loot table décrit. La table
est résolue par convention depuis l'id du bloc : `mineazur:<name>` -> `mineazur:blocks/<name>`
(cf. BlockBehaviour.Properties.drops). Sans fichier, la table est vide -> le bloc ne droppe RIEN en survie
(invisible en créatif, d'où le trou passé inaperçu). Ce script écrit une table standard « casse -> rend l'item »
pour chaque bloc, en lisant l'ordre depuis la source unique shared/mineazur_blocks.json.

TROIS CAS (depuis les params de la source unique) :
  * `perishable`  -> table VIDE : le sang ordinaire NE SE RAMASSE PAS. Une flaque, ça ne se récolte pas ; on en
                     obtient par craft (gen_recipes.py) et le sang éternel, lui, reste récupérable. Décision
                     utilisateur du 2026-07-15.
  * `noItem`      -> rend l'item du bloc de SOL correspondant. Les décals muraux n'ont pas d'item propre : un
                     « drop self » leur ferait rendre `mineazur:sang_mur`, un item QUI N'EXISTE PAS.
  * sinon         -> « drop self » standard.

Rejouable : à relancer si on ajoute un bloc. Miroir de gen_recipes.py.
"""
import json, os

BLOCKS_JSON = "/home/sebartyr/mineazur/shared/mineazur_blocks.json"
OUT = "/home/sebartyr/mineazur/Paper/paper-server/src/main/resources/data/mineazur/loot_table/blocks"
os.makedirs(OUT, exist_ok=True)

def drop_self(name):
    """Table de bloc vanilla-standard : 1 roll, drop l'item du même nom, survit à l'explosion."""
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"mineazur:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }

def empty():
    """Table explicitement VIDE : le bloc ne rend rien.

    Un fichier vide plutôt qu'un fichier ABSENT — les deux donnent le même résultat en jeu, mais l'absence se
    relit comme un oubli (c'est exactement ce qui avait fait passer inaperçu le trou des 23 blocs). Ici, c'est voulu.
    """
    return {"type": "minecraft:block", "pools": []}

blocks = json.load(open(BLOCKS_JSON))["blocks"]

# Les décals muraux n'ont pas d'item propre : ils rendent celui de leur bloc de sol, lequel les déclare via
# `wallVariant`. On inverse la relation pour retrouver le propriétaire depuis le mural.
owner = {b["params"]["wallVariant"]: b["name"] for b in blocks if b.get("params", {}).get("wallVariant")}

stats = {"self": 0, "vide": 0, "mural": 0}
for b in blocks:
    name = b["name"]
    p = b.get("params", {})
    if p.get("perishable"):
        table = empty(); stats["vide"] += 1
    elif p.get("noItem"):
        table = drop_self(owner[name]); stats["mural"] += 1
    else:
        table = drop_self(name); stats["self"] += 1
    json.dump(table, open(f"{OUT}/{name}.json", "w"), indent=2)

print(f"{len(blocks)} loot tables générées dans data/mineazur/loot_table/blocks/ : "
      f"{stats['self']} drop-self · {stats['vide']} VIDES (périssables) · {stats['mural']} muraux (item du sol)")
