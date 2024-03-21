#include "list.h"
#include "effectiveness.h"
#ifndef POKECHOOSER_H
#define POKECHOOSER_H

void pokeChooser(Pokemon pokemon, Type terraType);
void initPokeChooser();
void printChosen();
void insertChosen(BoxNode insert);

typedef struct NODE{
	BoxNode *pokemon;
	struct NODE *next;
}Node;

typedef struct CHOSEN{
	Node *first;
	int count;
}Chosen;
#endif
