#include "list.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>


int numberOfNewBoxInputs=0;
Type typeList1[19];
Type typeList2[19];
Box *box;
Pokedex *pokedex;
Pokemon* null_pokemon;

//levelup pokemon
/*
void levelUp(){

}
*/
//getter for box
Box getBox(){
return *box;
}
// initalize box
void initBox(){
 box=(Box *)malloc(sizeof(Box));
	box->first=NULL;
}
//given a list of 19 items(aka type lists) will initalize it for ye
void initTypes(Type thisList[19]){
	if(thisList[0].name==NULL){
		//printf("typelist1 is empty\n");
		Type *newType;
       		newType=(Type *)malloc(sizeof(Type));
        	newType->name="NULL";
        	newType->index=0;
		thisList[0]=*newType;
		
		Type *newType1;//aka normal
                newType1=(Type *)malloc(sizeof(Type));
                newType1->name="normal";
                newType1->index=1;
                thisList[1]=*newType1;

		Type *newType2;//aka fire
                newType2=(Type *)malloc(sizeof(Type));
                newType2->name="fire";
                newType2->index=2;
                thisList[2]=*newType2;

		Type *newType3;//aka water
                newType3=(Type *)malloc(sizeof(Type));
                newType3->name="water";
                newType3->index=3;
                thisList[3]=*newType3;

		Type *newType4;//aka grass
                newType4=(Type *)malloc(sizeof(Type));
                newType4->name="grass";
                newType4->index=4;
                thisList[4]=*newType4;

		Type *newType5;//aka electric
                newType5=(Type *)malloc(sizeof(Type));
                newType5->name="electric";
                newType5->index=5;
                thisList[5]=*newType5;

		Type *newType6;//aka ice
                newType6=(Type *)malloc(sizeof(Type));
                newType6->name="ice";
                newType6->index=6;
                thisList[6]=*newType6;

		Type *newType7;//aka fighting
                newType7=(Type *)malloc(sizeof(Type));
                newType7->name="fighting";
                newType7->index=7;
                thisList[7]=*newType7;

		Type *newType8;//aka poison
                newType8=(Type *)malloc(sizeof(Type));
                newType8->name="poison";
                newType8->index=8;
                thisList[8]=*newType8;

		Type *newType9;//aka ground 
                newType9=(Type *)malloc(sizeof(Type));
                newType9->name="ground";
                newType9->index=9;
                thisList[9]=*newType9;

		Type *newType10;//aka flying
                newType10=(Type *)malloc(sizeof(Type));
                newType10->name="flying";
                newType10->index=10;
                thisList[10]=*newType10;

		Type *newType11;//aka psychic
                newType11=(Type *)malloc(sizeof(Type));
                newType11->name="psychic";
                newType11->index=11;
                thisList[11]=*newType11;

		Type *newType12;//aka bug
                newType12=(Type *)malloc(sizeof(Type));
                newType12->name="bug";
                newType12->index=12;
                thisList[12]=*newType12;

		Type *newType13;//aka rock
                newType13=(Type *)malloc(sizeof(Type));
                newType13->name="rock";
                newType13->index=13;
                thisList[13]=*newType13;

		Type *newType14;//aka ghost
                newType14=(Type *)malloc(sizeof(Type));
                newType14->name="ghost";
                newType14->index=14;
                thisList[14]=*newType14;

		Type *newType15;//aka dragon
                newType15=(Type *)malloc(sizeof(Type));
                newType15->name="dragon";
                newType15->index=15;
                thisList[15]=*newType15;

		Type *newType16;//aka dark
                newType16=(Type *)malloc(sizeof(Type));
                newType16->name="dark";
                newType16->index=16;
                thisList[16]=*newType16;

		Type *newType17;//aka steel
                newType17=(Type *)malloc(sizeof(Type));
                newType17->name="steel";
                newType17->index=17;
                thisList[17]=*newType17;

		Type *newType18;//aka fairy
                newType18=(Type *)malloc(sizeof(Type));
                newType18->name="fairy";
                newType18->index=18;
                thisList[18]=*newType18;

	}// end of making types.. add else?
}
//add new member of box vi commands
// this will add a new pokemon to your box.txt file
void appendBox(int num,char* name, char* type, char* type2, int lvl,char* boxName){
if(box==NULL)
initBox();
	BoxNode *current= box->first;
	int alreadyInBox=0;
	printf("inserting %s with number %d and type %s and %s\n",name,num,type,type2);
	fflush(stdout);
	while(current!=NULL){
		if(current->pokemon->number==num || strcmp(name,current->pokemon->name)==0){
			alreadyInBox=1;
			printf("that name and/or number is already in box, are you sure you want to add another one? (y/n:)\n");
			char* line="n";
//			scanf("%s",line);
				if(strcmp(line,"y")==0){
					alreadyInBox=0;
					printf("that was a yes\n");
					fflush(stdout);
				}else{
					printf("for now only awnser is no\n");
					fflush(stdout);
					alreadyInBox=1;
					break;
				}
		}
		current=current->next;
	}
	if(alreadyInBox==0){
		insertBox(num,name,type,type2,lvl);
		FILE *fp2;
		fp2=fopen("box.txt","a");//make sure box is right name??!!
		if(!fp2){
			printf("could not find file\n");
		}else{
			if(numberOfNewBoxInputs==0){//fOpen already adds a \n so i have to account for that
				fprintf(fp2,"%d,%s,%s,%s,%d",num,name,type,type2,lvl);	
			}else{
				fprintf(fp2,"\n%d,%s,%s,%s,%d",num,name,type,type2,lvl);
			}
			fclose(fp2);
			numberOfNewBoxInputs++;
		}
		
	}
}
//enter pokemon into box
void insertBox(int num, char* name, char* type,char* type2,int lvl){
	for(int i = 0; name[i]; i++){
  name[i] = tolower(name[i]);
}
int inserted=0;
	if(box->first==NULL){
		for(int i=0;i<NUMBER_OF_POKEMON-1;i++){

			if(pokedex->arr[i]!=NULL && pokedex->arr[i]->name!=NULL && strcmp(pokedex->arr[i]->name,name)==0){
				BoxNode *newNode;
				newNode=(BoxNode *)malloc(sizeof(BoxNode));
				newNode->pokemon=pokedex->arr[i];
				newNode->lvl=lvl;
				newNode->next=NULL;
				box->first=newNode;
				inserted=1;
				break;
			}
		}
	}else if(box->first!=NULL){
		BoxNode *current=box->first;
		while(current->next!=NULL){
			current=current->next;
		}
		for(int i=0;i<NUMBER_OF_POKEMON-1;i++){
			if(pokedex->arr[i]!=NULL && strcmp(pokedex->arr[i]->name,name)==0){
				BoxNode *newNode;
				newNode=(BoxNode *)malloc(sizeof(BoxNode));
                                newNode->pokemon= pokedex->arr[i];
                                newNode->lvl=lvl;
				newNode->next=NULL;
				current->next=newNode;
				inserted=1;
                                break;
                        }
		}
	}
if(inserted==0){
	printf("%s was not entered, either mispelled or not in pokedex\n",name);
}
}
//print out pokemon in box line by line
void printBox(){
if(box!=NULL){
	BoxNode *current=box->first;
	printf("\n---printing box---\n");
		while(current!=NULL){
		printf("name: %s, number:%d, type1 %s, type2: %s, lvl:%d\n",current->pokemon->name, current->pokemon->number, current->pokemon->type1->name, current->pokemon->type2->name,current->lvl);
;
		current=current->next;
		}
	printf("\n---end of print---\n");
}else{
	printf("box not initalized, please enter:\"box\"\n");
}
}
//will search pokedex of name of pokemon
Pokemon findPokemonPokedex(char* lookfor){
	for(int i=0;i<NUMBER_OF_POKEMON-1;i++){//maybe <=
		if(pokedex->arr[i]!=NULL){
		  if( strcmp(pokedex->arr[i]->name,lookfor)==0 ){
			printf("found pokemon: %s.. matches %s\n",pokedex->arr[i]->name,lookfor);
			fflush(stdout);
			return *pokedex->arr[i];
		  }
		}
	}
return *null_pokemon;
}
//will print out the whole pokedex in order
void printPokedex(){
 printf("\n----PRINTING POKEDEX----\n");
 for(int i=0;i<NUMBER_OF_POKEMON;i++){//maybe <=
	if(pokedex->arr[i]!=NULL)
 		printf("name: %s, number:%d, type1 %s, type2: %s\n",pokedex->arr[i]->name, pokedex->arr[i]->number, pokedex->arr[i]->type1->name, pokedex->arr[i]->type2->name);
 }
 	printf("\n---END OF PRINT---\n");
 
}
//prints out all pokemon in pokedex line by line----
void printPokemon(char* pokemon){
 for(int i=0;i<NUMBER_OF_POKEMON;i++){//maybe <=
        if(pokedex->arr[i]!=NULL && strcmp(pokedex->arr[i]->name,pokemon)==0)
                printf("name: %s, number:%d, type1 %s, type2: %s\n",pokedex->arr[i]->name, pokedex->arr[i]->number, pokedex->arr[i]->type1->name, pokedex->arr[i]->type2->name);
	}
}
//get pokedex
Pokedex getPokedex(){
	return *pokedex;
}
//will insert pokemon into array pokedex
void insertPokedex(int num, char* name, char* type,char* type2){
 Pokemon *newPokemon;
 newPokemon=(Pokemon *)malloc(sizeof(Pokemon));
 newPokemon->number=num;
for(int i = 0; name[i]; i++){
  name[i] = tolower(name[i]);
}
type2[strlen(type2)-2]='\0';
 newPokemon->name=(strdup(name));
// printf("inserting %s, type is:%s and %s\n",newPokemon->name,type,type2);
//all the type assigments
  if(strcmp(type,"Normal")==0){
        newPokemon->type1=&typeList1[1];
 }else if(strcmp(type,"Fire")==0){
	newPokemon->type1=&typeList1[2];
 }else if(strcmp(type,"Water")==0){
	newPokemon->type1=&typeList1[3];
 }else if(strcmp(type,"Grass")==0){
	newPokemon->type1=&typeList1[4];
 }else if(strcmp(type,"Electric")==0){
	newPokemon->type1=&typeList1[5];
 }else if(strcmp(type,"Ice")==0){
	newPokemon->type1=&typeList1[6];
 }else if(strcmp(type,"Fighting")==0){
	newPokemon->type1=&typeList1[7];
 }else if(strcmp(type,"Poison")==0){
	newPokemon->type1=&typeList1[8];
 }else if(strcmp(type,"Ground")==0){
	newPokemon->type1=&typeList1[9];
 }else if(strcmp(type,"Flying")==0){
	newPokemon->type1=&typeList1[10];
 }else if(strcmp(type,"Psychic")==0){
	newPokemon->type1=&typeList1[11];
 }else if(strcmp(type,"Bug")==0){
	newPokemon->type1=&typeList1[12];
 }else if(strcmp(type,"Rock")==0){
	newPokemon->type1=&typeList1[13];
 }else if(strcmp(type,"Ghost")==0){
	newPokemon->type1=&typeList1[14];
 }else if(strcmp(type,"Dragon")==0){
	newPokemon->type1=&typeList1[15];
 }else if(strcmp(type,"Dark")==0){
	newPokemon->type1=&typeList1[16];
 }else if(strcmp(type,"Steel")==0){
	newPokemon->type1=&typeList1[17];
 }else if(strcmp(type,"Fairy")==0){
	newPokemon->type1=&typeList1[18];
 }
 //second type--------------
  if(strcmp(type2,"Normal")==0){
	newPokemon->type2=&typeList2[1];
 }else if(strcmp(type2,"Fire")==0){
	newPokemon->type2=&typeList2[2];
 }else if(strcmp(type2,"Water")==0){
	newPokemon->type2=&typeList2[3];
 }else if(strcmp(type2,"Grass")==0){
	newPokemon->type2=&typeList2[4];
 }else if(strcmp(type2,"Electric")==0){
	newPokemon->type2=&typeList2[5];
 }else if(strcmp(type2,"Ice")==0){
	newPokemon->type2=&typeList2[6];
 }else if(strcmp(type2,"Fighting")==0){
	newPokemon->type2=&typeList2[7];
 }else if(strcmp(type2,"Poison")==0){
	newPokemon->type2=&typeList2[8];
 }else if(strcmp(type2,"Ground")==0){
	newPokemon->type2=&typeList2[9];
 }else if(strcmp(type2,"Flying")==0){
	newPokemon->type2=&typeList2[10];
 }else if(strcmp(type2,"Psychic")==0){
	newPokemon->type2=&typeList2[11];
 }else if(strcmp(type2,"Bug")==0){
	newPokemon->type2=&typeList2[12];
 }else if(strcmp(type2,"Rock")==0){
	newPokemon->type2=&typeList2[13];
 }else if(strcmp(type2,"Ghost")==0){
	newPokemon->type2=&typeList2[14];
 }else if(strcmp(type2,"Dragon")==0){
	newPokemon->type2=&typeList2[15];
 }else if(strcmp(type2,"Dark")==0){
	newPokemon->type2=&typeList2[16];
 }else if(strcmp(type2,"Steel")==0){
 	newPokemon->type2=&typeList2[17];
 }else if(strcmp(type2,"Fairy")==0){
	newPokemon->type2=&typeList2[18];
 }else{
         newPokemon->type2=&typeList2[0];
}
///------------------------------------
//end of type assigment */
///------------------------------------
 if(pokedex->arr[num]==NULL){
	pokedex->arr[num]=newPokemon;
// printf("inserted %s\n",pokedex->arr[num]->name);
 }else{
	printf("that pokemon number already exists\n");
 }

}
//initalize null pokemon for return types of 'null'
void initNull_pokemon(){
        null_pokemon=(Pokemon *)malloc(sizeof(Pokemon));
        null_pokemon->name="NULL";
        null_pokemon->number=0;
        null_pokemon->type1= &typeList1[0];

}
//initalize pokedex and init types list
void initPokedex(){
	pokedex=(Pokedex *)malloc(sizeof(Pokedex));
	initTypes(typeList1);
	initTypes(typeList2);
	initNull_pokemon();
	pokedex->arr[0]=null_pokemon;	
}
