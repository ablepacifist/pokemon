#ifndef LIST_H
#define LIST_H


#ifndef NUMBER_OF_POKEMON
#define NUMBER_OF_POKEMON 1200//max box space in violet and scarlet
#endif
#include "types.h"

typedef struct POKEMON{//aka the pokemon 
 unsigned  int number;
   char* name;
     struct TYPE* type1;//initally struct TYPE* type1;
     struct TYPE* type2;

}Pokemon;

typedef struct POKEDEX{//aka pokedex
 struct POKEMON* arr[NUMBER_OF_POKEMON];
}Pokedex;

typedef struct BOXNODE{
 Pokemon *pokemon;
 int lvl;
 //int boxNum;??
 struct BOXNODE* next;
}BoxNode;

typedef struct BOX{
BoxNode *first;
}Box;
//protoTypes 
Type* makeType1(Pokemon newPokemon, char* name, int index);
void insertPokedex(int num, char* name, char* type, char* type2);
void initPokedex();
Pokedex getPokedex(); 
void initTypes(Type typeList[19]);
Pokemon findPokemonPokedex(char* name);
void printPokedex();
void initNull_pokemon();
void printPokemon(char* pokemon);
//box
Box getBox();
void initBox();
void insertBox();
void printBox();
void appendBox(int num,char* name, char* type, char* type2, int lvl,char* boxName);
#endif
