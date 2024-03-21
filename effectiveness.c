#include "effectiveness.h"
#include "list.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
int numTypes=19; //(18+1)
double effectiveness[19][19];
struct TYPELIST *typeList;
Type* weakness[19];
Type* strength[19];
//rows are defenders, collums are attackers!!!!
//first num is defender, second is attacker
//

//print strengths currently stored
void printStrength(){
//printf("weaknessCounter is at %d\n",weaknessCounter);
        for(int i=0;i<19;i++){
                if(strength[i]!=NULL && strength[i]->index!=0){
                        printf("-%s\n",strength[i]->name);
                }
        }
        }
//clear strength
void clearStrength(){
for(int i=0;i<numTypes;i++){
        strength[i]=NULL;
}
}
//find strengths given a type
void findStrengthType(Type type){
printf("%s strengths are against: \n",type.name);
        for(int i=1;i<numTypes;i++){
          if(effectiveness[i][type.index]>1){
                TypeNode *current=typeList->first;
                while(current!=NULL && current->type.index!=i){
                        current=current->next;
                }
                if(current!=NULL)
                printf("%s\n",current->type.name);
          }
        }

}

void findStrength(char* word){
	clearStrength();
	int isType=0;
	TypeNode *current=typeList->first;
	while(current!=NULL){
		if(strcmp(current->type.name,word)==0){
			isType=1;
			findStrengthType(current->type);
			break;
		}else{
			current=current->next;
		}
	}
if(isType==0){
Pokemon pokemon=findPokemonPokedex(word);
  int found1=0; //0 is false and 1 is true
        if(strcmp(pokemon.name,"NULL")){
                printf("%s is type: %s and %s",pokemon.name, pokemon.type1->name,pokemon.type2->name);
                printf("\n %s strengths are against:\n",pokemon.name);
                int strengthCounter=0;
        // type strengths
		//end test
                for(int i=1;i<19;i++){
                        TypeNode *current = typeList->first;
                        //type 2 strengths
                        if(effectiveness[i][pokemon.type2->index]>1){
                                //exclude any weaknesses of first type// ??maybe not need for strenghts
                               // if(effectiveness[pokemon.type1->index][i]>=1){
                                        current = typeList->first;
                                        found1=0;
                                        while(found1==0 && current!=NULL){
                                                if(current->type.index==i){
                                                        found1=1;
                                                        strength[strengthCounter]=&current->type;
                                                        strengthCounter++;
                                                }
                                        current=current->next;
                                        }
                               // }
                        }
                        //type 1 strengths
                        if(effectiveness[i][pokemon.type1->index]>1){
                                //exclude any weakness of second type// ?? maybe no need for strenghts
                               // if(effectiveness[pokemon.type2->index][i]>=1){
                                        current = typeList->first;
                                        found1=0;
                                        while(found1==0 && current!=NULL){
                                                if(current->type.index==i){
                                                        found1=1;
                                                        strength[strengthCounter]=&current->type;
                                                        strengthCounter++;
                                                }
                                                current=current->next;
                                        }
                                //}
 			}

                }
//print it out
	printStrength();

        }else{
                printf("is NULL\n");
        }
        }
}
//getter for type weakness
Type* getWeakness(){
	return *weakness;
}
//find weakness given a type
void findWeaknessType(Type type){
	printf("%s weaknesses are: \n",type.name);
	for(int i=1;i<19;i++){
	  if(effectiveness[type.index][i]>1){
		TypeNode *current=typeList->first;
		while(current!=NULL && current->type.index!=i){
			current=current->next;
		}
		if(current!=NULL){
			printf("%s\n",current->type.name);
			//typeWeaknessInsert(*current);
			 

		}
	  }
	}
   }
void clearWeakness(){
for(int i=0;i<numTypes;i++){
	weakness[i]=NULL;
}
}
// find weakness
void findWeakness(char* word){
clearWeakness();
	//identify contents of "word". type or pokemon
	int isType=0;
	TypeNode *current=typeList->first;
	while(current!=NULL){
		if(strcmp(current->type.name,word)==0){
			isType=1;
			findWeaknessType(current->type);
			break;
		}else{
		current=current->next;
		}
	}
if(isType==0){
Pokemon pokemon=findPokemonPokedex(word);
  int found1=0; //0 is false and 1 is true
	if(strcmp(pokemon.name,"NULL")){
		printf("%s is type: %s and %s",pokemon.name, pokemon.type1->name,pokemon.type2->name);	
		printf("\n %s weaknesses are:\n",pokemon.name);
		int weaknessCounter=0;
	// type weaknesses
		for(int i=1;i<19;i++){
			TypeNode *current = typeList->first;
			//type 2 weaknesses
			if(effectiveness[pokemon.type2->index][i]>1){
				//exclude any strengths of first type
				if(effectiveness[pokemon.type1->index][i]>=1){
					current = typeList->first;
					found1=0;
					while(found1==0 && current!=NULL){
						if(current->type.index==i){
							found1=1;
							weakness[weaknessCounter]=&current->type;
							weaknessCounter++;
						}
					current=current->next;
					}
				}
			}
			//type 1 weaknesses
			if(effectiveness[pokemon.type1->index][i]>1){
				//exclude any strengths of second type
				if(effectiveness[pokemon.type2->index][i]>=1){
					current = typeList->first;
					found1=0;
					while(found1==0 && current!=NULL){
						if(current->type.index==i){
							found1=1;
							weakness[weaknessCounter]=&current->type;
							weaknessCounter++;
						}
						current=current->next;
					}
				}
			}
		}
	}	
	}else{
	printf("is NULL\n");
	}		
	printWeakness();
	}
//print weaknesses currently stored
void printWeakness(){
//printf("weaknessCounter is at %d\n",weaknessCounter);
	for(int i=0;i<19;i++){
		if(weakness[i]!=NULL && weakness[i]->index!=0){
			printf("-%s\n",weakness[i]->name);
		}
	}
	
	}
// getter for a null_type
Type getNullType(){
	//check if typelist is init
	//missing
	return typeList->first->type;
}
// prints out the effectiveness chart kinda poorly
void printEffectivenessChart(){
	printf("\n");
	for(int i=0;i<19;i++){
		for(int j=0;j<19;j++){
		printf("%d| ",(int)effectiveness[i][j]);
		}
	printf("\n");
	}
}
//put stuff into list of types
void insertType(char* name, int index){
 Type *newType;
 newType=(Type *)malloc(sizeof(Type));
 newType->name=name;
 newType->index=index;

 TypeNode *newNode;
 newNode=(TypeNode *)malloc(sizeof(TypeNode));
 newNode->type=*newType;

 TypeNode *current=typeList->first;
 while(current->next!=NULL){
    current=current->next;
 }
 current->next=newNode;
}
//make the type list
void initTypeList(){
typeList=(TypeList *)malloc(sizeof(TypeList));
 Type *newType;
 newType=(Type *)malloc(sizeof(Type));
 newType->name="null";
 newType->index=0;

 TypeNode *newNode;
 newNode=(TypeNode *)malloc(sizeof(TypeNode));
 newNode->type=*newType;
 typeList->first=newNode;
 insertType("normal",1);
 insertType("fire",2);
 insertType("water",3);
 insertType("grass",4);
 insertType("electric",5);
 insertType("ice",6);
 insertType("fighting",7);
 insertType("poison",8);
 insertType("ground",9);
 insertType("flying",10);
 insertType("psychic",11);
 insertType("bug",12);
 insertType("rock",13);
 insertType("ghost",14);
 insertType("dragon",15);
 insertType("dark",16);
 insertType("steel",17);
 insertType("fairy",18);


}

//0 th row and colom are empty or all 1s
void initEffectiveness(){
if(effectiveness[0][0]!=1){
  initTypeList();
	for(int i=0;i<=18;i++){
		for(int j=0;j<=18;j++){
			effectiveness[i][j]=1;
		}
	}
//normal starts at 1 and fairy is 18 !!
//normal def
effectiveness[1][7]=2;
effectiveness[1][14]=0;
//fire
effectiveness[2][2]=.5;
effectiveness[2][3]=2;
effectiveness[2][4]=.5;
effectiveness[2][6]=.5;
effectiveness[2][9]=2;
effectiveness[2][12]=.5;
effectiveness[2][13]=2;
effectiveness[2][17]=.5;
effectiveness[2][18]=.5;
//water
effectiveness[3][2]=.5;
effectiveness[3][3]=.5;
effectiveness[3][4]=2;
effectiveness[3][5]=2;
effectiveness[3][6]=.5;
effectiveness[3][17]=.5;
//grass
effectiveness[4][2]=2;
effectiveness[4][3]=.5;
effectiveness[4][4]=.5;
effectiveness[4][5]=.5;
effectiveness[4][6]=2;
effectiveness[4][8]=2;
effectiveness[4][9]=.5;
effectiveness[4][10]=2;
effectiveness[4][12]=2;
//electric
effectiveness[5][5]=.5;
effectiveness[5][9]=2;
effectiveness[5][10]=.5;
effectiveness[5][17]=.5;
//ice
effectiveness[6][2]=2;
effectiveness[6][6]=.5;
effectiveness[6][7]=2;
effectiveness[6][13]=2;
effectiveness[6][17]=.5;
//fighting
effectiveness[7][10]=2;
effectiveness[7][11]=2;
effectiveness[7][12]=.5;
effectiveness[7][13]=.5;
effectiveness[7][16]=.5;
effectiveness[7][18]=2;
//poison
effectiveness[8][4]=.5;
effectiveness[8][7]=.5;
effectiveness[8][8]=.5;
effectiveness[8][9]=2;
effectiveness[8][11]=2;
effectiveness[8][12]=.5;
effectiveness[8][18]=.5;
//ground
effectiveness[9][3]=2;
effectiveness[9][4]=2;
effectiveness[9][5]=0;
effectiveness[9][6]=2;
effectiveness[9][8]=.5;
effectiveness[9][13]=.5;
//flying
effectiveness[10][4]=.5;
effectiveness[10][5]=2;
effectiveness[10][6]=2;
effectiveness[10][7]=.5;
effectiveness[10][9]=0;
effectiveness[10][12]=.5;
effectiveness[10][13]=2;
//psychic
effectiveness[11][7]=.5;
effectiveness[11][11]=.5;
effectiveness[11][12]=2;
effectiveness[11][14]=2;
effectiveness[11][16]=2;
//bug
effectiveness[12][2]=2;
effectiveness[12][4]=.5;
effectiveness[12][7]=.5;
effectiveness[12][9]=.5;
effectiveness[12][10]=2;
effectiveness[12][13]=2;
//rock
effectiveness[13][1]=.5;
effectiveness[13][2]=.5;
effectiveness[13][3]=2;
effectiveness[13][4]=2;
effectiveness[13][7]=2;
effectiveness[13][8]=.5;
effectiveness[13][9]=2;
effectiveness[13][10]=.5;
effectiveness[13][17]=2;
//ghost
effectiveness[14][1]=0;
effectiveness[14][7]=0;
effectiveness[14][8]=.5;
effectiveness[14][12]=.5;
effectiveness[14][14]=2;
effectiveness[14][16]=2;
//dragon
effectiveness[15][2]=.5;
effectiveness[15][3]=.5;
effectiveness[15][4]=.5;
effectiveness[15][5]=.5;
effectiveness[15][6]=2;
effectiveness[15][15]=2;
effectiveness[15][18]=2;
//dark
effectiveness[16][7]=2;
effectiveness[16][11]=0;
effectiveness[16][12]=2;
effectiveness[16][14]=.5;
effectiveness[16][16]=.5;
//steel
effectiveness[17][1]=.5;
effectiveness[17][2]=2;
effectiveness[17][4]=.5;
effectiveness[17][6]=.5;
effectiveness[17][7]=2;
effectiveness[17][8]=0;
effectiveness[17][9]=2;
effectiveness[17][10]=.5;
effectiveness[17][11]=.5;
effectiveness[17][12]=.5;
effectiveness[17][13]=.5;
effectiveness[17][15]=.5;
effectiveness[17][17]=.5;
effectiveness[17][18]=.5;
//fairy
effectiveness[18][7]=.5;
effectiveness[18][8]=2;
effectiveness[18][12]=.5;
effectiveness[18][15]=0;
effectiveness[18][16]=.5;
effectiveness[18][17]=2;
//oh boy
}


}
