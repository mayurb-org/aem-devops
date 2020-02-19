# bare clone of main repo
git clone --bare https://gitlab.digitas.com/dunkin-brands/br-olo-extract.git
cd br-olo-extract.git
# add br-olo origin
git remote add --mirror=fetch broloorigin git@dbuslnxgithub01.dunkinbrands.corp:dunkindonuts/br-olo-extract.git
# pull in latest changes from DTAS origin
git fetch origin --tags
# pull in latest changes from brolo origin
git fetch broloorigin --tags
# sync all repos
#git push origin --all
git push broloorigin --all