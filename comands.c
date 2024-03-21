#include "list.h"
#include "types.h"
#include "effectiveness.h"
#include "pokeChooser.h"
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include "comands.h"
void comands(){
 char boxName[40]="";
 char line[60]="";

initAll();

 printf("enter a comand:\n");
 int exitflag=0;
 while(exitflag==0){
	scanf("%s",line);
	if(strcmp(line,"help")==0){
	printf("all comands are lower case:\n \"exit\":end program\n \"pokedex\":initalize pokedex(needs correct txt file)\n\"box\" will initalize box with given txt file(MUST INIT POKEDEX FIRST)\n  \"weakness\":prints type weakness of given pokemon or type\n \"print\": will print out all pokemon in pokedex or box\n \"lookup\": will print info on pokemon you give it\n");
	}else if(strcmp(line,"exit")==0){
	exitflag=1;
	}else if(strcmp(line,"pokedex")==0){
	printf("choose file:\n");	
	scanf("%s",line);
	FILE *fp = fopen(line,"r");
	if(!fp){
	printf("cant find file name\n");
	}else{
		while(fgets(line, 255,fp)){
			char *pch =strtok(line,",");
			int number=atoi(pch);
			 pch =strtok(NULL,",");
        	        char* name =(pch);
			 pch =strtok(NULL,",");
       		         char* type1=(pch);
			 pch = strtok(NULL,",");
			char* type2=(pch);
			insertPokedex(number,name,type1,type2);
		}
		fclose(fp);
	}
	}else if(strcmp(line,"box")==0){
        printf("choose file:\n");
        scanf("%s",line);
	for(int i=0;i<40;i++){
	boxName[i]=line[i];
	}
        FILE *fp = fopen(line,"r");
        if(!fp){
        printf("cant find file name");
        }else{
                while(fgets(line, 255,fp)){
       	       		char *pch =strtok(line,",");
                	int number=atoi(pch);
	                pch =strtok(NULL,",");
        	        char* name =(pch);
                	pch =strtok(NULL,",");
	                char* type1=(pch);
        	        pch = strtok(NULL,",");
                	char* type2=(pch);
			pch = strtok(NULL,",");
			int lvl=atoi(pch);
                	insertBox(number,name,type1,type2,lvl);
                }
        	fclose(fp);
	}
	}else if(strcmp(line,"deposit")==0){
		if(strcmp(boxName,"")==0){
	                printf("box not initalized, please enter \"box\"\n");
                }else{
			printf("enter name:\n");
			scanf("%s",line);			
			Pokemon newPokemon=findPokemonPokedex(line);
			if(newPokemon.number!=0){	
				printf("enter pokemon lvl:\n");
				int newPokemonLevel=1;
				scanf("%d",&newPokemonLevel);
				appendBox(newPokemon.number,newPokemon.name,newPokemon.type1->name,newPokemon.type2->name,newPokemonLevel,boxName);
			}else{
			 printf("could not find pokemon");
			}
		}
	}else if(strcmp(line,"weakness")==0){
		printf("enter Pokemon or Type:\n");
		scanf("%s",line);
                findWeakness(line);

	}else if(strcmp(line,"strength")==0 || strcmp(line,"strengths")==0){
		printf("enter Pokemon or Type:\n");
		scanf("%s",line);
		findStrength(line);
	}else if(strcmp(line,"print")==0){
		printf("pokedex or box\n");
		scanf("%s",line);
		if(strcmp(line,"pokedex")==0){
			printPokedex();
		}else if(strcmp(line,"box")==0){
			printBox();
		}
		
	}else if(strcmp(line,"lookup")==0){
		printf("enter pokemon:\n");
		scanf("%s",line);
		printPokemon(line);
	}else if(strcmp(line,"chart")==0){
		printEffectivenessChart();

	}else if(strcmp(line,"choose")==0){
		printf("enter pokemon:\n");
		scanf("%s",line);
		Pokemon thisPokemon = findPokemonPokedex(line);
		if(thisPokemon.number!=0){
			//printf("impleentation coming soon...\n");
			pokeChooser(thisPokemon,getNullType());
			printChoosen();	
		}else{
			printf("cannot find mon\n");
		}
	}
 }
 printf("\nend of program\n");
}
void initAll(){
initPokedex();
printf("pokedex init\n");
fflush(stdout);
initBox();
printf( "box Init\n");
fflush(stdout);
initEffectiveness();
printf("effectiveness init\n");
fflush(stdout);
printf("type init\n");
initTypeList();
}
