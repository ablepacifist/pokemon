#ifndef TYPE_H
#define TYPE_H
typedef struct TYPE{
 char* name;
 int index;
/*
  struct NORMAL normal;
  struct FIRE fire;
  struct WATER water;
  struct GRASS grass;
  struct ELECTRIC electric;
  struct ICE ice;
  struct FIGHTING fighting;
  struct POISON poison;
  struct GROUND ground;
  struct FLYING flying;
  struct PSYCHIC psychic;
  struct BUG bug;
  struct ROCK rock;
  struct GHOST ghost;
  struct DRAGON dragon;
  struct DARK dark;
  struct STEEL steel;
  struct FAIRY fairy;
*/
}Type;



typedef struct TYPENODE{
 struct TYPE type;
 struct TYPENODE *next;
}TypeNode;

typedef struct TYPELIST{
 struct TYPENODE *first;
 int isInit;
}TypeList;


//the following is no longer used:
/*
struct NORMAL{
 char* typeName;
 int index;
//typeName={'n','o','r','m','a','l'};
};

struct FIRE {
char* typeName;
int index;

};

struct WATER{
char* typeName;
int index;

};

struct GRASS{
char* typeName;
int index;

};

struct ELECTRIC{
char* typeName;
int index;

};

struct ICE{
char* typeName;
int index;

};

struct FIGHTING{
char* typeName;
int index;

};

struct POISON{
char* typeName;
int index;

};

struct GROUND{
char* typeName;
int index;

};

struct FLYING{
char* typeName;
int index;

};

struct PSYCHIC{
char* typeName;
int index;

};

struct BUG{
char* typeName;
int index;

};

struct ROCK{
char* typeName;
int index;

};

struct GHOST{
char* typeName;
int index;

};

struct DRAGON{
char* typeName;
int index;

};

struct DARK{
char* typeName;
int index;

};

struct STEEL{
char* typeName;
int index;

};

struct FAIRY{
char* typeName;
int index;

};
*/
#endif

