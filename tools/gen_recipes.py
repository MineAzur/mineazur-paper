#!/usr/bin/env python3
"""Génère les recettes MineAzur (portage de SBTBlocks.enregistrerCrafts/Fontes) au format 26.1.2."""
import json, os
OUT = "/home/sebartyr/mineazur/Paper/paper-server/src/main/resources/data/mineazur/recipe"
os.makedirs(OUT, exist_ok=True)

def w(name, obj):
    json.dump(obj, open(f"{OUT}/{name}.json", "w"), indent=2)

def shaped(name, pattern, key, result, count=1):
    w(name, {"type":"minecraft:crafting_shaped","pattern":pattern,"key":key,
             "result":{"id":result,"count":count}})

def shapeless(name, ingredients, result, count=1):
    w(name, {"type":"minecraft:crafting_shapeless","ingredients":ingredients,
             "result":{"id":result,"count":count}})

def smelt(name, ingredient, result, xp=0.2, time=200):
    w(name, {"type":"minecraft:smelting","ingredient":ingredient,"result":{"id":result},
             "experience":xp,"cookingtime":time})

M = "mineazur:"; V = "minecraft:"

# --- matériau / smelting ---
smelt("obsidian_ingot", V+"obsidian", M+"obsidian_ingot", xp=1.0)

# --- outils obsidienne (# = ingot, I = stick) ---
tool_key = {"#": M+"obsidian_ingot", "I": V+"stick"}
shaped("obsidian_pickaxe", ["###"," I "," I "], tool_key, M+"obsidian_pickaxe")
shaped("obsidian_shovel",  [" # "," I "," I "], tool_key, M+"obsidian_shovel")
shaped("obsidian_axe",     [" ##"," I#"," I "], tool_key, M+"obsidian_axe")
shaped("obsidian_hoe",     [" ##"," I "," I "], tool_key, M+"obsidian_hoe")
shaped("obsidian_sword",   [" # "," # "," I "], tool_key, M+"obsidian_sword")

# --- armure obsidienne (X = ingot) ---
ak = {"X": M+"obsidian_ingot"}
shaped("obsidian_helmet",     ["XXX","X X"],       ak, M+"obsidian_helmet")
shaped("obsidian_chestplate", ["X X","XXX","XXX"], ak, M+"obsidian_chestplate")
shaped("obsidian_leggings",   ["XXX","X X","X X"], ak, M+"obsidian_leggings")
shaped("obsidian_boots",      ["X X","X X"],       ak, M+"obsidian_boots")

# --- lanterne ---
shaped("lanterne", ["igi","gtg","igi"], {"i":V+"iron_bars","g":V+"glass","t":V+"torch"}, M+"lanterne")

# --- escaliers de laine (# = laine couleur) -> 4 ---
COLORS = ["white","orange","magenta","light_blue","yellow","lime","pink","gray",
          "light_gray","cyan","purple","blue","brown","green","red","black"]
for c in COLORS:
    shaped(f"stairs_wool_{c}", ["#  ","## ","###"], {"#":V+f"{c}_wool"}, M+f"stairs_wool_{c}", 4)

# --- blocs custom ---
shaped("plafond", ["###"], {"#":V+"oak_slab"}, M+"plafond", 4)
shaped("table", ["#","0"], {"#":M+"plafond","0":V+"oak_fence"}, M+"table")
shaped("chair", ["S# ","#X#","# #"], {"#":V+"oak_fence","X":M+"plafond","S":M+"vernis"}, M+"chair")
shaped("toit", ["  X"," XX","XXX"], {"X":M+"bois_verni"}, M+"toit", 4)
shaped("bois_verni", ["XXX","X#X","XXX"], {"X":V+"oak_planks","#":M+"vernis"}, M+"bois_verni", 4)
shaped("dalle", ["XX","XX"], {"X":V+"stone_bricks"}, M+"dalle", 4)
# sang (bloc NOUVEAU, connecté) : oeil d'araignee + poudre de redstone -> gros rendement.
shapeless("sang", [V+"spider_eye", V+"redstone", V+"redstone", V+"redstone", V+"redstone"], M+"sang", 8)

# --- items divers / nourriture ---
shaped("chope", ["# #","# #","###"], {"#":V+"glass"}, M+"chope", 4)
shapeless("biere", [V+"wheat", M+"chope"], M+"biere")   # simplifié (potion d'origine omise)
shaped("pizza", ["0#0","MMM"], {"M":M+"farine","0":V+"brown_mushroom","#":V+"egg"}, M+"pizza", 2)
shapeless("farine", [V+"wheat"], M+"farine", 2)
shaped("mayo", ["   ","&#&","XXX"], {"&":V+"egg","#":V+"milk_bucket","X":V+"glass"}, M+"mayo", 8)
shaped("zsandwich", [" X ","&#&"," X "], {"#":M+"mayo","X":V+"bread","&":V+"cooked_beef"}, M+"zsandwich")
shaped("vernis", ["   "," X "," # "], {"#":V+"glass_bottle","X":V+"cactus"}, M+"vernis")
shaped("shuriken", [" # ","# #"," # "], {"#":V+"iron_ingot"}, M+"shuriken", 16)

n = len(os.listdir(OUT))
print(f"{n} recettes générées dans data/mineazur/recipe/")
