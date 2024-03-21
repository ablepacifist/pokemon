# a sample Makefile

CC = clang
CFLAGS = -g -Wall # -DNDEBUG

PROG = test
HDRS = list.h types.h effectiveness.h comands.h pokeChooser.h
SRCS = main.c list.c effectiveness.c comands.c pokeChooser.c

OBJDIR = object
OBJS = $(OBJDIR)/main.o $(OBJDIR)/list.o $(OBJDIR)/effectiveness.o $(OBJDIR)/comands.o $(OBJDIR)/pokeChooser.o

# compiling rules

# WARNING: *must* have a tab before each definition
$(PROG): $(OBJDIR) $(OBJS)
	$(CC) $(CFLAGS) $(OBJS) -o $(PROG)

$(OBJDIR)/pokeChooser.o: pokeChooser.c $(HDRS)
	$(CC) $(CFLAGS) -c pokeChooser.c -o $(OBJDIR)/pokeChooser.o

$(OBJDIR)/comands.o: comands.c $(HDRS)
	$(CC) $(CFLAGS) -c comands.c -o $(OBJDIR)/comands.o

$(OBJDIR)/list.o: list.c $(HDRS)
	$(CC) $(CFLAGS) -c list.c -o $(OBJDIR)/list.o

$(OBJDIR)/effectiveness.o: effectiveness.c $(HDRS)
	$(CC) $(CFLAGS) -c effectiveness.c -o $(OBJDIR)/effectiveness.o

$(OBJDIR)/main.o: main.c $(HDRS)
	$(CC) $(CFLAGS) -c main.c -o $(OBJDIR)/main.o

$(OBJDIR):
	mkdir $(OBJDIR)

clean:
	rm -f $(PROG) $(OBJS)
	rmdir $(OBJDIR)
