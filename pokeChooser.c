#include "pokeChooser.h"
#include "list.h"
#include "effectiveness.h"
#include "types.h"
#include <stdio.h>
#include <stdlib.h>
 Chosen *chosen;

void pokeChooser(Pokemon pokemon, Type terraType){
	if(terraType.index==0){
		findWeakness(pokemon.name);
		Type*	weakness=getWeakness();
		Box box = getBox();
printf("GOT BOX\n");
fflush(stdout);
		int i;
		BoxNode *current= box.first;
		for(i=0;i<19;i++){//replace 19 with number of types
			if(weakness[i].name!=NULL){
				while(current!=NULL){
					if(weakness[i].index == current->pokemon->type1->index || weakness[i].index == current->pokemon->type2->index){
						//add to chosen list
						insertChosen(*current);
					}
					current=current->next;
				}
			}	
		}
	}
}
void printChoosen(){
	Node *current=chosen->first;
	printf("\nUse these pokemon in your box:\n");
	while(current!=NULL){
		printf("-%s, level:%d\n",current->pokemon->pokemon->name,current->pokemon->lvl);
		current=current->next;
	}
}
void insertChosen(BoxNode insert){
	Node *current=chosen->first;
	while(current->next!=NULL){
		current=current->next;
	}
	Node *newChosen;
	newChosen=(Node *)malloc(sizeof(Node));
	newChosen->pokemon=&insert;
	current->next=newChosen;

}
void initPokeChooser(){
	chosen=(Chosen *)malloc(sizeof(Chosen));
	Node *newNode;
	newNode = (Node *)malloc(sizeof(Node));
		BoxNode *newBoxNode;
		newBoxNode = (BoxNode *)malloc(sizeof(BoxNode));
		newBoxNode->pokemon = getPokedex().arr[0];
		newNode->pokemon=newBoxNode;
	chosen->first = newNode;
}

