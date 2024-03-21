
#ifndef EFFECTIVENESS
#define EFFECTIVENESS
#include "types.h"
#include "list.h"
//int effectiveness[18][18];//rows are attackers, collums are defenders

void initEffectiveness();
void printEffectivenessChart();
void initTypeWeakness();

void printStrength();
void findStrength(char* word);
void findStrenthType(Type type);
void clearStrength();

void findWeaknessType(Type type);
void  findWeakness(char* word);
void initTypeList();
void insertType(char* name, int index);

Type getNullType();
void printWeakness();
Type* getWeakness();
void typeWeaknessInsert(TypeNode insert);
void clearWeakness();
#endif
